package br.com.lhamalog.core.identity.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.ProtectedTestController;
import br.com.lhamalog.core.support.RecordingSessionRevocation;
import br.com.lhamalog.core.support.TestJwks;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;

/** End-to-end behaviour of the Clerk session token validation (spec US1, US2, US3). */
@CoreIntegrationTest
class ClerkTokenAuthenticationTests {

	private static final String ACTIVE_ORG = "org_active";

	private static final String SUSPENDED_ORG = "org_suspended";

	private static final String TENANT_ID = "tenant-active-0001";

	private static final String UNAUTHORIZED = "{\"error\":\"unauthorized\"}";

	private static final String FORBIDDEN = "{\"error\":\"forbidden\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private RecordingSessionRevocation revocation;

	@BeforeEach
	void setUp() {
		revocation.reset();
		mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), TenantDocument.class);
		mongoTemplate.insert(new TenantDocument(TENANT_ID, ACTIVE_ORG, "Active tenant", "ACTIVE"));
		mongoTemplate.insert(new TenantDocument("tenant-suspended-0001", SUSPENDED_ORG, "Suspended", "SUSPENDED"));
	}

	// US1: valid token and active tenant

	@Test
	void validTokenWithActiveTenantExposesRequestContext() throws Exception {
		call(TestJwks.token().organization(ACTIVE_ORG, "admin").bearer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.clerkUserId").value("user_test"))
			.andExpect(jsonPath("$.sessionId").value("sess_test"))
			.andExpect(jsonPath("$.clerkOrgId").value(ACTIVE_ORG))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.tenantId").value(TENANT_ID));
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void prefixedRoleIsNormalized() throws Exception {
		call(TestJwks.token().organization(ACTIVE_ORG, "org:member").bearer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("MEMBER"));
	}

	@Test
	void unknownRouteWithValidTokenIsNotFoundWithoutRevocation() throws Exception {
		mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION,
				TestJwks.token().organization(ACTIVE_ORG, "admin").bearer()))
			.andExpect(status().isNotFound());
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	// US2: 401 with a basic, identical body

	@Test
	void missingTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get(ProtectedTestController.PATH)).andExpect(status().isUnauthorized())
			.andExpect(content().json(UNAUTHORIZED, true));
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		expectUnauthorized("Bearer not-a-jwt");
	}

	@Test
	void foreignSignatureIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().signedWithForeignKey().bearer());
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		Instant past = Instant.now().minusSeconds(120);
		expectUnauthorized(validToken().claim("iat", past).claim("nbf", past).claim("exp", past.plusSeconds(60))
			.bearer());
	}

	@Test
	void notYetValidTokenIsUnauthorized() throws Exception {
		Instant future = Instant.now().plusSeconds(120);
		expectUnauthorized(validToken().claim("nbf", future).claim("exp", future.plusSeconds(60)).bearer());
	}

	@Test
	void missingExpirationIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().without("exp").bearer());
	}

	@Test
	void missingNotBeforeIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().without("nbf").bearer());
	}

	@Test
	void otherIssuerIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().claim("iss", "https://other.clerk.invalid").bearer());
	}

	@Test
	void unauthorizedPartyIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().claim("azp", "https://evil.example").bearer());
	}

	@Test
	void missingAuthorizedPartyIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().without("azp").bearer());
	}

	@Test
	void pendingSessionIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().claim("sts", "pending").bearer());
	}

	@Test
	void missingSessionStatusIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().without("sts").bearer());
	}

	@Test
	void missingSessionIdIsUnauthorized() throws Exception {
		expectUnauthorized(validToken().without("sid").bearer());
	}

	// US3: 403, basic body, sessions revoked

	@Test
	void missingOrganizationIsForbiddenAndRevokesSessions() throws Exception {
		expectForbiddenAndRevoked(validToken().without("o").subject("user_no_org").bearer(), "user_no_org");
	}

	@Test
	void organizationWithoutTenantIsForbiddenAndRevokesSessions() throws Exception {
		expectForbiddenAndRevoked(TestJwks.token().organization("org_unknown", "admin").subject("user_x").bearer(),
				"user_x");
	}

	@Test
	void suspendedTenantIsForbidden() throws Exception {
		expectForbiddenAndRevoked(TestJwks.token().organization(SUSPENDED_ORG, "admin").bearer(), "user_test");
	}

	@Test
	void unknownRoleIsForbidden() throws Exception {
		expectForbiddenAndRevoked(TestJwks.token().organization(ACTIVE_ORG, "billing").bearer(), "user_test");
	}

	@Test
	void missingRoleIsForbidden() throws Exception {
		expectForbiddenAndRevoked(TestJwks.token().organization(ACTIVE_ORG, null).bearer(), "user_test");
	}

	@Test
	void revocationFailureKeepsForbidden() throws Exception {
		revocation.failNextCalls();
		expectForbiddenAndRevoked(TestJwks.token().organization("org_unknown", "admin").bearer(), "user_test");
	}

	private TestJwks.Token validToken() {
		return TestJwks.token().organization(ACTIVE_ORG, "admin");
	}

	private ResultActions call(String authorization) throws Exception {
		return mockMvc.perform(get(ProtectedTestController.PATH).header(HttpHeaders.AUTHORIZATION, authorization));
	}

	private void expectUnauthorized(String authorization) throws Exception {
		call(authorization).andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().json(UNAUTHORIZED, true));
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	private void expectForbiddenAndRevoked(String authorization, String clerkUserId) throws Exception {
		call(authorization).andExpect(status().isForbidden()).andExpect(content().json(FORBIDDEN, true));
		assertThat(revocation.revokedUsers()).containsExactly(clerkUserId);
	}

}
