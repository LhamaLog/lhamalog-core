package br.com.lhamalog.core.identity.adapter.in.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers every authentication failure with the same basic 401, without saying why (ADR_032). Spring's default
 * bearer entry point would put the reason in {@code WWW-Authenticate}.
 */
public final class BasicErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

	static final String BODY = "{\"error\":\"unauthorized\"}";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(BODY);
	}

}
