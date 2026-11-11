package br.com.lhamalog.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
public class UsageFactsSecurityConfiguration {
	private static final String SERVICE_TOKEN_HEADER = "X-LhamaLog-Service-Token";

	@Bean
	@Order(1)
	SecurityFilterChain usageFactsSecurityFilterChain(HttpSecurity http,
			@Value("${LHAMALOG_CORE_USAGE_TOKEN:}") String configuredToken) throws Exception {
		http.securityMatcher("/internal/usage-facts", "/internal/usage-facts/**",
				"/internal/usage-reservations", "/internal/usage-reservations/**")
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.addFilterBefore(new ServiceTokenFilter(configuredToken), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	static String normalizeToken(String token) {
		if (token == null) {
			return null;
		}
		String value = token.strip();
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if (first == last && (first == '"' || first == '\'')) {
				value = value.substring(1, value.length() - 1).strip();
			}
		}
		return value;
	}

	static String sha256Hex(String value) {
		if (value == null) {
			return "none";
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static final class ServiceTokenFilter extends OncePerRequestFilter {
		private static final Logger log = LoggerFactory.getLogger(ServiceTokenFilter.class);
		private final byte[] expectedToken;
		private final String expectedFingerprint;

		private ServiceTokenFilter(String configuredToken) {
			String normalized = normalizeToken(configuredToken);
			this.expectedToken = normalized == null ? new byte[0] : normalized.getBytes(StandardCharsets.UTF_8);
			this.expectedFingerprint = sha256Hex(normalized == null ? "" : normalized);
			log.info("Core usage token configured length={} sha256={}", expectedToken.length, expectedFingerprint);
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			String token = normalizeToken(request.getHeader(SERVICE_TOKEN_HEADER));
			boolean authorizationPresent = request.getHeader("Authorization") != null;
			String reason = null;
			if (authorizationPresent) {
				reason = "authorization-header-present";
			}
			else if (token == null) {
				reason = "missing-service-token";
			}
			else if (expectedToken.length == 0) {
				reason = "server-token-not-configured";
			}
			else if (!MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
				reason = "token-mismatch";
			}
			if (reason != null) {
				log.warn(
						"Rejected {} {} ({}) contentType={} authorizationPresent={} receivedTokenLength={} receivedSha256={} expectedLength={} expectedSha256={}",
						request.getMethod(), request.getRequestURI(), reason, request.getContentType(),
						authorizationPresent, token == null ? 0 : token.length(), sha256Hex(token),
						expectedToken.length, expectedFingerprint);
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			filterChain.doFilter(request, response);
		}
	}
}
