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

	@Test
	void acceptedInviteRolesFollowAdministratorList() {
		CompanySessionResolver resolver = new CompanySessionResolver("admin");
		assertTrue(resolver.resolve(current("org_a", "admin")).administrator());
		assertTrue(resolver.resolve(current("org_a", "org:admin")).administrator());
		assertFalse(resolver.resolve(current("org_a", "member")).administrator());
		assertFalse(resolver.resolve(current("org_a", "org:member")).administrator());
		assertFalse(resolver.resolve(current("org_a", "Administrador")).administrator());
		assertEquals("org_a", resolver.resolve(current("org_a", "member")).tenantId());
		assertTrue(resolver.resolve(legacy("org_b", "admin")).administrator());
		assertTrue(resolver.resolve(legacy("org_b", "org:admin")).administrator());
		assertFalse(resolver.resolve(legacy("org_b", "member")).administrator());
		assertFalse(resolver.resolve(legacy("org_b", "org:member")).administrator());
		assertEquals("org_b", resolver.resolve(legacy("org_b", "member")).tenantId());
	}

	private static Jwt current(String organization, String role) {
		return Jwt.withTokenValue("test").header("alg", "RS256").subject("user")
				.claim("o", Map.of("id", organization, "rol", role)).build();
	}

	private static Jwt legacy(String organization, String role) {
		return Jwt.withTokenValue("test").header("alg", "RS256").subject("user")
				.claim("org_id", organization).claim("org_role", role).build();
	}
}
