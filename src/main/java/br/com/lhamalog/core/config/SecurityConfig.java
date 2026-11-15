package br.com.lhamalog.core.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless Clerk authentication. Health and info remain public.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
			.permitAll()
			.anyRequest()
			.authenticated())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf((csrf) -> csrf.disable())
			.cors(Customizer.withDefaults())
			.oauth2ResourceServer((resourceServer) -> resourceServer.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${clerk.issuer:}") String issuer,
			@Value("${clerk.jwks-uri:}") String jwksUri,
			@Value("${clerk.authorized-parties:}") String authorizedParties) {
		if (issuer.isBlank() || jwksUri.isBlank() || authorizedParties.isBlank()) {
			return token -> { throw new BadJwtException("Clerk authentication is not configured"); };
		}
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer),
				new ClerkTokenValidator(origins(authorizedParties))));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${clerk.authorized-parties:}") String authorizedParties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.copyOf(origins(authorizedParties)));
		configuration.setAllowedMethods(List.of("GET", "PUT", "POST", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	private static Set<String> origins(String value) {
		return Arrays.stream(value.split(",")).map(String::trim)
				.filter(origin -> !origin.isBlank()).collect(Collectors.toSet());
	}
}
