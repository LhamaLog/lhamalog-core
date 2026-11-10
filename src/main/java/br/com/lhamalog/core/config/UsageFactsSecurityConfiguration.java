package br.com.lhamalog.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
		http.securityMatcher("/internal/usage-facts", "/internal/usage-facts/**")
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.addFilterBefore(new ServiceTokenFilter(configuredToken), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	private static final class ServiceTokenFilter extends OncePerRequestFilter {
		private final byte[] expectedToken;

		private ServiceTokenFilter(String configuredToken) {
			this.expectedToken = configuredToken.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			String token = request.getHeader(SERVICE_TOKEN_HEADER);
			if (request.getHeader("Authorization") != null || token == null || expectedToken.length == 0
					|| !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			filterChain.doFilter(request, response);
		}
	}
}
