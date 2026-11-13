package br.com.lhamalog.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.identity.domain.OrganizationRole;
import br.com.lhamalog.core.identity.domain.RequestContext;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

class CompanySessionTests {

	private static RequestContext context(OrganizationRole role) {
		return new RequestContext("user_a", "sess_a", "org_a", role, "tenant-0001");
	}

	@Test
	void onlyOrganizationAdminIsAdministrator() {
		assertTrue(CompanySession.from(context(OrganizationRole.ADMIN)).administrator());
		assertFalse(CompanySession.from(context(OrganizationRole.MEMBER)).administrator());
	}

	@Test
	void usesInternalTenantIdAndClerkUser() {
		CompanySession session = new CompanySessionResolver(() -> context(OrganizationRole.MEMBER)).current();
		assertEquals("tenant-0001", session.tenantId());
		assertEquals("user_a", session.userId());
	}

}
