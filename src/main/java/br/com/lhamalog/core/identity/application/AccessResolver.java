package br.com.lhamalog.core.identity.application;

import java.util.Optional;

import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.identity.domain.AccessDecision.Denied;
import br.com.lhamalog.core.identity.domain.AccessDecision.Granted;
import br.com.lhamalog.core.identity.domain.AccessDecision.Reason;
import br.com.lhamalog.core.identity.domain.ClerkSessionClaims;
import br.com.lhamalog.core.identity.domain.OrganizationRole;
import br.com.lhamalog.core.identity.domain.RequestContext;
import br.com.lhamalog.core.tenant.application.port.out.TenantRepository;
import br.com.lhamalog.core.tenant.domain.Tenant;

/**
 * Decides whether an authenticated Clerk session may access the Core (ADR_032/121): it needs an active organization,
 * a known role and an active tenant mirroring that organization.
 */
public class AccessResolver {

	private final TenantRepository tenants;

	public AccessResolver(TenantRepository tenants) {
		this.tenants = tenants;
	}

	public AccessDecision resolve(ClerkSessionClaims claims) {
		if (!claims.hasActiveOrganization()) {
			return new Denied(Reason.NO_ACTIVE_ORGANIZATION);
		}
		Optional<OrganizationRole> role = OrganizationRole.fromClerk(claims.organizationRole());
		if (role.isEmpty()) {
			return new Denied(Reason.UNKNOWN_ROLE);
		}
		Optional<Tenant> tenant = tenants.findByClerkOrgId(claims.organizationId());
		if (tenant.isEmpty()) {
			return new Denied(Reason.TENANT_NOT_FOUND);
		}
		if (!tenant.get().isActive()) {
			return new Denied(Reason.TENANT_INACTIVE);
		}
		return new Granted(new RequestContext(claims.subject(), claims.sessionId(), claims.organizationId(), role.get(),
				tenant.get().tenantId()));
	}

}
