package br.com.lhamalog.core.identity.adapter.in.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import br.com.lhamalog.core.identity.domain.AccessDecision;

/**
 * Grants the purchase routes ({@code /api/billing/**}, spec 010) to a Clerk session that would be granted anyway or
 * whose only problem is that its organization has no tenant yet. A missing organization, an unknown role or a
 * suspended tenant is still refused with the security 403.
 */
public final class BillingAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		return new AuthorizationDecision(authentication.get() instanceof ClerkAuthentication clerk
				&& (clerk.decision() instanceof AccessDecision.Granted || withoutTenant(clerk.decision())));
	}

	static boolean withoutTenant(AccessDecision decision) {
		return decision instanceof AccessDecision.Denied denied
				&& denied.reason() == AccessDecision.Reason.TENANT_NOT_FOUND;
	}

}
