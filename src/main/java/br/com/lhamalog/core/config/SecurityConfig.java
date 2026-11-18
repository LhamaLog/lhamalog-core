package br.com.lhamalog.core.config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.lhamalog.core.identity.adapter.in.security.ActiveSessionValidator;
import br.com.lhamalog.core.identity.adapter.in.security.AuthorizedPartyValidator;
import br.com.lhamalog.core.identity.adapter.in.security.BasicErrorAuthenticationEntryPoint;
import br.com.lhamalog.core.identity.adapter.in.security.ClerkAuthenticationConverter;
import br.com.lhamalog.core.identity.adapter.in.security.SessionRevokingAccessDeniedHandler;
import br.com.lhamalog.core.identity.adapter.in.security.TenantAccessAuthorizationManager;
import br.com.lhamalog.core.identity.adapter.in.security.UnavailableKeysAsInvalidTokenJwtDecoder;
import br.com.lhamalog.core.identity.application.port.out.SessionRevocation;

import jakarta.servlet.DispatcherType;

/**
 * Stateless security of the Core (LLT-163). Every route except health/info and the Clerk webhook requires a valid
 * Clerk session token ({@code Authorization: Bearer}) and an active tenant. 401 and 403 have basic bodies
 * (ADR_032); every 403 revokes the user's Clerk sessions.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	static final String CLERK_WEBHOOK_PATH = "/webhooks/clerk";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			ClerkAuthenticationConverter clerkAuthenticationConverter, SessionRevocation sessionRevocation,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		BasicErrorAuthenticationEntryPoint entryPoint = new BasicErrorAuthenticationEntryPoint();
		SessionRevokingAccessDeniedHandler accessDeniedHandler = new SessionRevokingAccessDeniedHandler(
				sessionRevocation);
		http.authorizeHttpRequests((requests) -> requests
			// Error pages rendered after a response status was already decided.
			.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
			.permitAll()
			// Authenticated by the Svix signature, not by a session token (LLT-164).
			.requestMatchers(HttpMethod.POST, CLERK_WEBHOOK_PATH)
			.permitAll()
			.anyRequest()
			.access(new TenantAccessAuthorizationManager()))
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(clerkAuthenticationConverter))
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.cors((cors) -> cors.configurationSource(corsConfigurationSource))
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf((csrf) -> csrf.disable())
			.httpBasic((basic) -> basic.disable())
			.formLogin((form) -> form.disable())
			.logout((logout) -> logout.disable());
		return http.build();
	}

	/**
	 * Validates RS256 signature against the Clerk JWKS, exp/nbf (both required), iss, azp and sts (ADR_022).
	 * {@link JwtTimestampValidator} alone accepts a token without exp/nbf, so their presence is checked explicitly.
	 */
	@Bean
	JwtDecoder jwtDecoder(ClerkProperties clerk) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(clerk.resolvedJwksUri())
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
				new JwtClaimValidator<Object>(JwtClaimNames.NBF, Objects::nonNull),
				new JwtTimestampValidator(clockSkew(clerk)), new JwtIssuerValidator(clerk.issuer()),
				new AuthorizedPartyValidator(clerk.authorizedParties()), new ActiveSessionValidator()));
		return new UnavailableKeysAsInvalidTokenJwtDecoder(decoder);
	}

	/** Same origins as the authorized parties; the token travels in a header, so no credentials. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(ClerkProperties clerk) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(clerk.authorizedParties());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private static Duration clockSkew(ClerkProperties clerk) {
		return (clerk.clockSkew() != null) ? clerk.clockSkew() : Duration.ofSeconds(5);
	}

}
