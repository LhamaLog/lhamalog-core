package br.com.lhamalog.core.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security of the Occurrence Module routes {@code /internal/v1/**} (spec 013, research R1): HS256 JWT signed with a
 * shared secret (up to two, for rotation), with {@code iss}, {@code aud}, {@code sub}, {@code exp} and {@code nbf}
 * checked, and only the configured services ({@code sub}) allowed. It runs before the Clerk chain and does not touch
 * {@code /internal/usage-*}. Without a secret every call is refused (fail closed); the Core still starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OccurrenceModuleProperties.class)
public class InternalServiceSecurityConfiguration {

	static final String PATHS = "/internal/v1/**";

	static final String SERVICE_AUTHORITY = "SERVICE_OCCURRENCE_MODULE";

	private static final int MIN_SECRET_BYTES = 32;

	private static final Logger log = LoggerFactory.getLogger(InternalServiceSecurityConfiguration.class);

	@Bean
	@Order(2)
	SecurityFilterChain internalServiceSecurityFilterChain(HttpSecurity http, OccurrenceModuleProperties properties)
			throws Exception {
		OccurrenceModuleProperties.InternalAuth auth = properties.internalAuth();
		JwtDecoder decoder = decoder(auth);
		http.securityMatcher(PATHS)
			.authorizeHttpRequests((requests) -> requests.anyRequest().hasAuthority(SERVICE_AUTHORITY))
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.decoder(decoder).jwtAuthenticationConverter(converter(auth.subjects())))
				.authenticationEntryPoint((request, response, failure) -> unauthorized(response))
				.accessDeniedHandler((request, response, failure) -> forbidden(response)))
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint((request, response, failure) -> unauthorized(response))
				.accessDeniedHandler((request, response, failure) -> forbidden(response)))
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf((csrf) -> csrf.disable())
			.httpBasic((basic) -> basic.disable())
			.formLogin((form) -> form.disable())
			.logout((logout) -> logout.disable());
		return http.build();
	}

	/** Tries each secret in order; a token signed with any of them and valid for the claims passes. */
	static JwtDecoder decoder(OccurrenceModuleProperties.InternalAuth auth) {
		List<NimbusJwtDecoder> decoders = new ArrayList<>();
		for (String secret : auth.secrets()) {
			byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
			if (bytes.length < MIN_SECRET_BYTES) {
				log.warn("event=occurrence.auth.secret_ignored | Internal JWT secret shorter than 32 bytes ignored | length={}",
						bytes.length);
				continue;
			}
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
					new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
					new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull),
					new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(auth.issuer()),
					new JwtClaimValidator<Object>(JwtClaimNames.AUD,
							(audience) -> audience instanceof Collection<?> values && values.contains(auth.audience()))));
			decoders.add(decoder);
		}
		if (decoders.isEmpty()) {
			log.warn("event=occurrence.auth.not_configured | No internal JWT secret configured; /internal/v1 refuses every call");
		}
		else {
			log.info("event=occurrence.auth.configured | Internal JWT ready | secrets={} issuer={} audience={} subjects={}",
					decoders.size(), auth.issuer(), auth.audience(), auth.subjects().size());
		}
		return (token) -> {
			JwtException last = new BadJwtException("Internal JWT is not configured");
			for (NimbusJwtDecoder decoder : decoders) {
				try {
					return decoder.decode(token);
				}
				catch (JwtException refused) {
					last = refused;
				}
			}
			throw last;
		};
	}

	/** Only the configured services get the authority of the routes; another valid service gets 403. */
	private static JwtAuthenticationConverter converter(List<String> subjects) {
		Set<String> allowed = Set.copyOf(subjects);
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>();
			if (allowed.contains(jwt.getSubject())) authorities.add(new SimpleGrantedAuthority(SERVICE_AUTHORITY));
			return authorities;
		});
		return converter;
	}

	private static void unauthorized(HttpServletResponse response) throws java.io.IOException {
		response.setHeader("WWW-Authenticate", "Bearer");
		write(response, HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO");
	}

	private static void forbidden(HttpServletResponse response) throws java.io.IOException {
		write(response, HttpStatus.FORBIDDEN, InternalApiException.ACCESS_DENIED);
	}

	private static void write(HttpServletResponse response, HttpStatus status, String code) throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"codigo\":\"" + code + "\"}");
	}

}
