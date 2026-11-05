package br.com.lhamalog.core.config;

import java.util.Map;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;

class CompanySessionTests {
	@Test
	void mapsOnlyConfiguredPrivilegedRoles() {
		Jwt owner = Jwt.withTokenValue("test").header("alg", "RS256").subject("user")
				.claim("o", Map.of("id", "org_a", "rol", "owner")).build();
		assertFalse(new CompanySessionResolver("admin").resolve(owner).administrator());
		assertTrue(new CompanySessionResolver("admin,org:owner").resolve(owner).administrator());
	}
	@Test
	void supportsLegacyOrganizationClaimsWithoutChangingTenant() {
		Jwt legacy = Jwt.withTokenValue("test").header("alg", "RS256").subject("user")
				.claim("org_id", "org_a").claim("org_role", "org:admin").build();
		var session = new CompanySessionResolver("admin").resolve(legacy);
		assertEquals("org_a", session.tenantId());
		assertTrue(session.administrator());
	}
}
