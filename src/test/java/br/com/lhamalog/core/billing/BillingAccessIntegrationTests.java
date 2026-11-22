package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Spec 010, US2: an organization without tenant is sent to the purchase, never signed out for that. */
@CoreIntegrationTest
class BillingAccessIntegrationTests {

	private static final String PLANS = "/api/billing/plans";

	private static final String FORBIDDEN = "{\"error\":\"forbidden\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private RecordingSessionRevocation revocation;

	@BeforeEach
	void setUp() {
		revocation.reset();
	}

	@Test
	void organizationWithoutTenantGetsSubscriptionRequiredOnOtherRoutesAndKeepsItsSession() throws Exception {
		perform(ProtectedTestController.PATH, BillingFixtures.bearer(BillingFixtures.newOrg(), "user_a", "admin"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("subscription_required"))
			.andExpect(jsonPath("$.message").isNotEmpty());
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void organizationWithoutTenantCanListThePlans() throws Exception {
		perform(PLANS, BillingFixtures.bearer(BillingFixtures.newOrg(), "user_a", "member"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].plan").value("FREE"))
			.andExpect(jsonPath("$[0].price").value(0.0))
			.andExpect(jsonPath("$[0].currency").value("BRL"))
			.andExpect(jsonPath("$[1].plan").value("STARTER"))
			.andExpect(jsonPath("$[1].price").value(49.9));
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void organizationWithTenantCanListThePlansToo() throws Exception {
		String org = BillingFixtures.newOrg();
		mongo.insert(new TenantDocument("tenant-" + org, org, "Com tenant", "ACTIVE"));
		perform(PLANS, BillingFixtures.bearer(org, "user_a", "admin")).andExpect(status().isOk());
	}

	@Test
	void missingOrganizationStillGetsTheSecurityForbiddenOnPurchaseRoutes() throws Exception {
		perform(PLANS, TestJwks.token().without("o").subject("user_no_org").bearer())
			.andExpect(status().isForbidden())
			.andExpect(content().json(FORBIDDEN, true));
		assertThat(revocation.revokedUsers()).containsExactly("user_no_org");
	}

	@Test
	void unknownRoleStillGetsTheSecurityForbidden() throws Exception {
		perform(PLANS, BillingFixtures.bearer(BillingFixtures.newOrg(), "user_role", "billing"))
			.andExpect(status().isForbidden())
			.andExpect(content().json(FORBIDDEN, true));
		assertThat(revocation.revokedUsers()).containsExactly("user_role");
	}

	@Test
	void suspendedTenantStillGetsTheSecurityForbidden() throws Exception {
		String org = BillingFixtures.newOrg();
		mongo.insert(new TenantDocument("tenant-" + org, org, "Suspensa", "SUSPENDED"));
		perform(PLANS, BillingFixtures.bearer(org, "user_susp", "admin")).andExpect(status().isForbidden())
			.andExpect(content().json(FORBIDDEN, true));
		assertThat(revocation.revokedUsers()).containsExactly("user_susp");
	}

	@Test
	void missingTokenIsUnauthorizedOnPurchaseRoutes() throws Exception {
		mockMvc.perform(get(PLANS)).andExpect(status().isUnauthorized());
	}

	private ResultActions perform(String path, String authorization) throws Exception {
		return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization));
	}

}
