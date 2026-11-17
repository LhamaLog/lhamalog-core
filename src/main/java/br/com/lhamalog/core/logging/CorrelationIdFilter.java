package br.com.lhamalog.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = CorrelationId.resolve(request.getHeader(CorrelationId.HEADER));
		MDC.put(CorrelationId.KEY, correlationId);
		response.setHeader(CorrelationId.HEADER, correlationId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(CorrelationId.KEY);
		}
	}
}
