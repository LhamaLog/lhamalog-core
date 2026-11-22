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

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.ProtectedTestController;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;

/** Spec 010, US1: a confirmed payment activates the tenant (FR-009). */
@CoreIntegrationTest
class AsaasWebhookIntegrationTests {

	private static final String RECEIVED = "{\"received\":true}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AsaasWebhookCredential credential;

	private BillingFixtures fixtures;

	@BeforeEach
	void setUp() {
		fixtures = new BillingFixtures(mockMvc, mongo, credential);
		fixtures.clean();
	}

	@Test
	void paymentReceivedCreatesTenantSubscriptionAndOwnerAndOpensTheCore() throws Exception {
		String org = BillingFixtures.newOrg();
		String admin = BillingFixtures.bearer(org, "user_owner", "admin");
		String orderId = fixtures.order(admin, "STARTER").get("orderId").asString();
		String eventId = BillingFixtures.eventId();

		fixtures.webhook(BillingFixtures.paymentEvent(eventId, "PAYMENT_RECEIVED", orderId, 49.9))
			.andExpect(status().isOk())
			.andExpect(content().json(RECEIVED, true));

		TenantDocument tenant = fixtures.tenant(org);
		assertThat(tenant.status()).isEqualTo("ACTIVE");
		assertThat(tenant.name()).isEqualTo("Empresa Teste");
		assertThat(tenant.originOrderId()).isEqualTo(orderId);
		var subscription = fixtures.subscription(tenant.tenantId());
		assertThat(subscription.planTier()).isEqualTo("STARTER");
		assertThat(subscription.status()).isEqualTo("ACTIVE");
		assertThat(subscription.orderId()).isEqualTo(orderId);
		assertThat(fixtures.members(tenant.tenantId())).singleElement().satisfies((member) -> {
			assertThat(member.clerkUserId()).isEqualTo("user_owner");
			assertThat(member.role()).isEqualTo("DONO");
		});
		assertThat(fixtures.storedOrder(orderId).status()).isEqualTo("PAID");
		var event = fixtures.event(eventId);
		assertThat(event.status()).isEqualTo("EXECUTED");
		assertThat(event.tenantId()).isEqualTo(tenant.tenantId());
		assertThat(event.orderId()).isEqualTo(orderId);
		assertThat(event.attempts()).isEqualTo(1);

		mockMvc.perform(get(ProtectedTestController.PATH).header(HttpHeaders.AUTHORIZATION, admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantId").value(tenant.tenantId()));
		fixtures.latestOrder(admin)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.subscriptionActive").value(true));
	}

	@Test
	void cardConfirmationAlsoActivates() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = fixtures.order(BillingFixtures.bearer(org, "user_owner", "admin"), "PRO")
			.get("orderId")
			.asString();
		fixtures.webhook(BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_CONFIRMED", orderId, 149.9))
			.andExpect(status().isOk());
		assertThat(fixtures.tenants(org)).isOne();
	}

	@Test
	void freePlanAlsoNeedsTheConfirmation() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = fixtures.order(BillingFixtures.bearer(org, "user_owner", "admin"), "FREE")
			.get("orderId")
			.asString();
		assertThat(fixtures.tenants(org)).isZero();
		fixtures.webhook(BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_RECEIVED", orderId, 0))
			.andExpect(status().isOk());
		assertThat(fixtures.subscription(fixtures.tenant(org).tenantId()).planTier()).isEqualTo("FREE");
	}

	@Test
	void pendingOrderWithoutConfirmationGivesNoAccess() throws Exception {
		String org = BillingFixtures.newOrg();
		String admin = BillingFixtures.bearer(org, "user_owner", "admin");
		fixtures.order(admin, "STARTER");
		mockMvc.perform(get(ProtectedTestController.PATH).header(HttpHeaders.AUTHORIZATION, admin))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("subscription_required"));
	}

}
