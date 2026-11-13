package br.com.lhamalog.core.identity.adapter.in.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import br.com.lhamalog.core.identity.domain.AccessDecision;

/**
 * Grants protected routes only to a Clerk session whose access was resolved as granted. Anything else (anonymous,
 * denied tenant access) is refused; Spring then answers 401 for anonymous and 403 for an authenticated user.
 */
public final class TenantAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		return new AuthorizationDecision(authentication.get() instanceof ClerkAuthentication clerk
				&& clerk.decision() instanceof AccessDecision.Granted);
	}

}
