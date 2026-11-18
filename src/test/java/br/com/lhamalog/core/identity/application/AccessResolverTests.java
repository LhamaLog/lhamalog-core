package br.com.lhamalog.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.identity.domain.AccessDecision.Denied;
import br.com.lhamalog.core.identity.domain.AccessDecision.Granted;
import br.com.lhamalog.core.identity.domain.AccessDecision.Reason;
import br.com.lhamalog.core.identity.domain.ClerkSessionClaims;
import br.com.lhamalog.core.identity.domain.OrganizationRole;
import br.com.lhamalog.core.identity.domain.RequestContext;
import br.com.lhamalog.core.tenant.domain.Tenant;
import br.com.lhamalog.core.tenant.domain.TenantStatus;

/** Unit tests of the access rules, without Spring. */
class AccessResolverTests {

	private final Map<String, Tenant> tenants = Map.of("org_active",
			new Tenant("tenant-a", "org_active", "Active", TenantStatus.ACTIVE), "org_suspended",
			new Tenant("tenant-s", "org_suspended", "Suspended", TenantStatus.SUSPENDED));

	private final AccessResolver resolver = new AccessResolver(
			(clerkOrgId) -> Optional.ofNullable(tenants.get(clerkOrgId)));

	@Test
	void grantsActiveTenantWithKnownRole() {
		AccessDecision decision = resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", "org_active", "member"));

		assertThat(decision).isEqualTo(new Granted(
				new RequestContext("user_1", "sess_1", "org_active", OrganizationRole.MEMBER, "tenant-a")));
	}

	@Test
	void deniesWithoutActiveOrganization() {
		assertThat(resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", null, null)))
			.isEqualTo(new Denied(Reason.NO_ACTIVE_ORGANIZATION));
		assertThat(resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", " ", "admin")))
			.isEqualTo(new Denied(Reason.NO_ACTIVE_ORGANIZATION));
	}

	@Test
	void deniesUnknownRole() {
		assertThat(resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", "org_active", "billing")))
			.isEqualTo(new Denied(Reason.UNKNOWN_ROLE));
	}

	@Test
	void deniesOrganizationWithoutTenant() {
		assertThat(resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", "org_other", "admin")))
			.isEqualTo(new Denied(Reason.TENANT_NOT_FOUND));
	}

	@Test
	void deniesInactiveTenant() {
		assertThat(resolver.resolve(new ClerkSessionClaims("user_1", "sess_1", "org_suspended", "admin")))
			.isEqualTo(new Denied(Reason.TENANT_INACTIVE));
	}

}
