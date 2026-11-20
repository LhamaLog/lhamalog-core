package br.com.lhamalog.core.identity.adapter.in.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.lhamalog.core.identity.application.port.CurrentOrganizationSession;
import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.identity.domain.ClerkSessionClaims;
import br.com.lhamalog.core.identity.domain.OrganizationRole;
import br.com.lhamalog.core.identity.domain.OrganizationSession;
import br.com.lhamalog.core.identity.domain.RequestContext;

/** Reads the {@link OrganizationSession} from Spring Security's context of the current request. */
public final class SecurityContextCurrentOrganizationSession implements CurrentOrganizationSession {

	@Override
	public OrganizationSession current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof ClerkAuthentication clerk) {
			if (clerk.decision() instanceof AccessDecision.Granted granted) {
				RequestContext context = granted.context();
				return new OrganizationSession(context.clerkUserId(), context.clerkOrgId(), context.role(),
						context.tenantId());
			}
			if (BillingAccessAuthorizationManager.withoutTenant(clerk.decision())) {
				ClerkSessionClaims claims = clerk.claims();
				OrganizationRole role = OrganizationRole.fromClerk(claims.organizationRole())
					.orElseThrow(() -> new IllegalStateException("No known organization role"));
				return new OrganizationSession(claims.subject(), claims.organizationId(), role, null);
			}
		}
		throw new IllegalStateException("No organization session");
	}

}
