package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.out.mongo.SubscriptionOrderDocument;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingSessionRevocation;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import tools.jackson.databind.JsonNode;

/** Spec 010, US1: purchase orders (contracts/api.md, POST /api/billing/checkout and GET /api/billing/order). */
@CoreIntegrationTest
class CheckoutIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AsaasWebhookCredential credential;

	@Autowired
	private RecordingSessionRevocation revocation;

	private BillingFixtures fixtures;

	@BeforeEach
	void setUp() {
		fixtures = new BillingFixtures(mockMvc, mongo, credential);
		fixtures.clean();
		revocation.reset();
	}

	@Test
	void administratorWithoutTenantCreatesAPendingOrderWithASimulatedPaymentPage() throws Exception {
		String org = BillingFixtures.newOrg();
		fixtures.checkout(BillingFixtures.bearer(org, "user_admin", "admin"), "starter", "  Empresa Teste  ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderId").isNotEmpty())
			.andExpect(jsonPath("$.plan").value("STARTER"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.paymentUrl").value(Matchers.startsWith("http://localhost:8080/dev/fake-asaas/checkouts/pay_fake_")));
		var stored = mongo.findAll(SubscriptionOrderDocument.class)
			.getFirst();
		assertThat(stored.clerkOrgId()).isEqualTo(org);
		assertThat(stored.clerkUserId()).isEqualTo("user_admin");
		assertThat(stored.companyName()).isEqualTo("Empresa Teste");
		assertThat(stored.price()).isEqualByComparingTo("49.90");
		assertThat(fixtures.tenants(org)).isZero();
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void unknownPlanIsRejected() throws Exception {
		fixtures.checkout(BillingFixtures.bearer(BillingFixtures.newOrg(), "user_admin", "admin"), "GOLD", "Empresa")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_plan"));
		assertThat(mongo.count(new Query(),
				SubscriptionOrderDocument.class)).isZero();
	}

	@Test
	void invalidCompanyNameIsRejected() throws Exception {
		fixtures.checkout(BillingFixtures.bearer(BillingFixtures.newOrg(), "user_admin", "admin"), "PRO", " x ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.companyName").isNotEmpty());
	}

	@Test
	void memberWhoIsNotAdministratorCannotBuyAndStaysSignedIn() throws Exception {
		fixtures.checkout(BillingFixtures.bearer(BillingFixtures.newOrg(), "user_member", "member"), "PRO", "Empresa")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_admin"));
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void organizationThatAlreadyHasATenantCannotBuyAgain() throws Exception {
		String org = BillingFixtures.newOrg();
		mongo.insert(new TenantDocument("tenant-" + org, org, "Ativa", "ACTIVE"));
		fixtures.checkout(BillingFixtures.bearer(org, "user_admin", "admin"), "PRO", "Empresa")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_subscribed"));
	}

	@Test
	void newerOrderCancelsThePendingOne() throws Exception {
		String admin = BillingFixtures.bearer(BillingFixtures.newOrg(), "user_admin", "admin");
		JsonNode first = fixtures.order(admin, "STARTER");
		Thread.sleep(20); // distinct createdAt (stored with millisecond precision)
		JsonNode second = fixtures.order(admin, "PRO");
		assertThat(fixtures.storedOrder(first.get("orderId").asString()).status()).isEqualTo("CANCELED");
		assertThat(fixtures.storedOrder(second.get("orderId").asString()).status()).isEqualTo("PENDING");
		fixtures.latestOrder(admin)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(second.get("orderId").asString()))
			.andExpect(jsonPath("$.plan").value("PRO"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.subscriptionActive").value(false));
	}

	@Test
	void latestOrderWithoutOrdersIsNotFound() throws Exception {
		fixtures.latestOrder(BillingFixtures.bearer(BillingFixtures.newOrg(), "user_admin", "admin"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("no_order"));
	}

}
