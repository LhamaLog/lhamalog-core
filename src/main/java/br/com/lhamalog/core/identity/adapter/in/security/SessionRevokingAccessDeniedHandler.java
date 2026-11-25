package br.com.lhamalog.core.identity.adapter.in.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.client.RestClientResponseException;

import br.com.lhamalog.core.identity.application.port.out.SessionRevocation;
import br.com.lhamalog.core.identity.domain.AccessDecision;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Every 403 of the security chain lands here: the user's Clerk sessions are revoked so they start the login again
 * (ADR_032), then a basic 403 is written. A revocation failure is only logged and never changes the response.
 * <p>
 * Exception (spec 010): when the only problem is that the organization has no tenant yet, the session stays valid and
 * the 403 carries the business code {@code subscription_required}, so the Web sends the person to the plans instead
 * of signing them out.
 */
public final class SessionRevokingAccessDeniedHandler implements AccessDeniedHandler {

	static final String BODY = "{\"error\":\"forbidden\"}";

	static final String SUBSCRIPTION_REQUIRED_CODE = "subscription_required";

	static final String SUBSCRIPTION_REQUIRED_BODY = "{\"message\":\"Sua empresa ainda não tem um plano ativo. "
			+ "Contrate um plano para continuar.\",\"code\":\"" + SUBSCRIPTION_REQUIRED_CODE + "\"}";

	private static final Logger log = LoggerFactory.getLogger(SessionRevokingAccessDeniedHandler.class);

	private final SessionRevocation sessionRevocation;

	public SessionRevokingAccessDeniedHandler(SessionRevocation sessionRevocation) {
		this.sessionRevocation = sessionRevocation;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof ClerkAuthentication clerk
				&& BillingAccessAuthorizationManager.withoutTenant(clerk.decision())) {
			log.info("event=access.denied.subscription_required | Organization without tenant; session kept"
					+ " | clerkUserId={} clerkOrgId={}", clerk.claims().subject(), clerk.claims().organizationId());
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write(SUBSCRIPTION_REQUIRED_BODY);
			return;
		}
		if (authentication instanceof ClerkAuthentication clerk) {
			String clerkUserId = clerk.claims().subject();
			String reason = (clerk.decision() instanceof AccessDecision.Denied denied) ? denied.reason().name()
					: "PERMISSION_DENIED";
			log.info("event=access.denied.revoked | Access denied; revoking the user's Clerk sessions"
					+ " | clerkUserId={} reason={}", clerkUserId, reason);
			revokeQuietly(clerkUserId);
		}
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(BODY);
	}

	private void revokeQuietly(String clerkUserId) {
		try {
			sessionRevocation.revokeAllActiveSessions(clerkUserId);
		}
		catch (RestClientResponseException ex) {
			log.warn("event=access.revocation.failed | Clerk session revocation failed | clerkUserId={} status={}",
					clerkUserId,
					ex.getStatusCode().value());
		}
		catch (RuntimeException ex) {
			log.warn("event=access.revocation.failed | Clerk session revocation failed | clerkUserId={} error={}",
					clerkUserId,
					ex.getClass().getSimpleName());
		}
	}

}
