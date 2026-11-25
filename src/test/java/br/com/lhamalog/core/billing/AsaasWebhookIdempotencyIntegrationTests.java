package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.out.mongo.GatewayEventDocument;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;

/** Spec 010, US3: repeated, forged or unexpected confirmations never leave a wrong tenant behind. */
@CoreIntegrationTest
class AsaasWebhookIdempotencyIntegrationTests {

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
	void sameEventTwiceCreatesEverythingOnce() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = order(org, "STARTER");
		String body = BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_RECEIVED", orderId, 49.9);
		fixtures.webhook(body).andExpect(status().isOk());
		fixtures.webhook(body).andExpect(status().isOk());
		String tenantId = fixtures.tenant(org).tenantId();
		assertThat(fixtures.tenants(org)).isOne();
		assertThat(fixtures.subscriptions(tenantId)).isOne();
		assertThat(fixtures.members(tenantId)).hasSize(1);
		assertThat(mongo.findAll(GatewayEventDocument.class)).singleElement()
			.satisfies((event) -> assertThat(event.attempts()).isEqualTo(1));
	}

	@Test
	void wrongOrMissingTokenIsRejectedAndNothingIsStored() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = order(org, "STARTER");
		String body = BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_RECEIVED", orderId, 49.9);
		fixtures.webhook(body, "x".repeat(40))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"error\":\"invalid_token\"}", true));
		fixtures.webhook(body, null).andExpect(status().isUnauthorized());
		assertThat(fixtures.tenants(org)).isZero();
		assertThat(mongo.count(new Query(), GatewayEventDocument.class)).isZero();
	}

	@Test
	void malformedBodyIsRejected() throws Exception {
		fixtures.webhook("{\"event\":\"PAYMENT_RECEIVED\"}").andExpect(status().isBadRequest());
		fixtures.webhook("not json").andExpect(status().isBadRequest());
		assertThat(mongo.count(new Query(), GatewayEventDocument.class)).isZero();
	}

	@Test
	void unknownOrderIsRecordedAsFailedWithoutAskingForAResend() throws Exception {
		String eventId = BillingFixtures.eventId();
		fixtures.webhook(BillingFixtures.paymentEvent(eventId, "PAYMENT_RECEIVED", "no-such-order", 10))
			.andExpect(status().isOk());
		assertThat(fixtures.event(eventId).status()).isEqualTo("FAILED");
		assertThat(fixtures.event(eventId).reason()).isEqualTo("unknown_order");
		assertThat(fixtures.event(eventId).tenantId()).isNull();
	}

	@Test
	void otherEventTypesAreIgnored() throws Exception {
		String org = BillingFixtures.newOrg();
		String eventId = BillingFixtures.eventId();
		fixtures.webhook(BillingFixtures.paymentEvent(eventId, "PAYMENT_CREATED", order(org, "PRO"), 149.9))
			.andExpect(status().isOk());
		assertThat(fixtures.event(eventId).status()).isEqualTo("IGNORED");
		assertThat(fixtures.event(eventId).reason()).isEqualTo("event_not_handled");
		assertThat(fixtures.tenants(org)).isZero();
	}

	@Test
	void receivedAfterConfirmedOfTheSameOrderIsIgnored() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = order(org, "PRO");
		fixtures.webhook(BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_CONFIRMED", orderId, 149.9))
			.andExpect(status().isOk());
		String second = BillingFixtures.eventId();
		fixtures.webhook(BillingFixtures.paymentEvent(second, "PAYMENT_RECEIVED", orderId, 149.9))
			.andExpect(status().isOk());
		assertThat(fixtures.event(second).status()).isEqualTo("IGNORED");
		assertThat(fixtures.event(second).reason()).isEqualTo("order_already_paid");
		assertThat(fixtures.tenants(org)).isOne();
	}

	@Test
	void twoPaidOrdersOfTheSameOrganizationLeaveOneTenant() throws Exception {
		String org = BillingFixtures.newOrg();
		String first = order(org, "STARTER");
		Thread.sleep(20);
		String second = order(org, "PRO");
		// The older (canceled) order was paid anyway: it activates, because the money was received.
		fixtures.webhook(BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_RECEIVED", first, 49.9))
			.andExpect(status().isOk());
		String secondEvent = BillingFixtures.eventId();
		fixtures.webhook(BillingFixtures.paymentEvent(secondEvent, "PAYMENT_RECEIVED", second, 149.9))
			.andExpect(status().isOk());
		assertThat(fixtures.tenants(org)).isOne();
		assertThat(fixtures.tenant(org).originOrderId()).isEqualTo(first);
		assertThat(fixtures.subscription(fixtures.tenant(org).tenantId()).planTier()).isEqualTo("STARTER");
		assertThat(fixtures.event(secondEvent).reason()).isEqualTo("tenant_already_active");
		assertThat(fixtures.storedOrder(second).status()).isEqualTo("PAID");
	}

	@Test
	void organizationThatGotATenantElsewhereIsNotChanged() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = order(org, "STARTER");
		mongo.insert(new TenantDocument("tenant-seed-" + org, org, "Tenant de desenvolvimento", "ACTIVE"));
		String eventId = BillingFixtures.eventId();
		fixtures.webhook(BillingFixtures.paymentEvent(eventId, "PAYMENT_RECEIVED", orderId, 49.9))
			.andExpect(status().isOk());
		assertThat(fixtures.event(eventId).reason()).isEqualTo("tenant_already_active");
		assertThat(fixtures.tenant(org).name()).isEqualTo("Tenant de desenvolvimento");
		assertThat(fixtures.subscriptions("tenant-seed-" + org)).isZero();
	}

	@Test
	void differentValueStillActivatesWithThePlanOfTheOrder() throws Exception {
		String org = BillingFixtures.newOrg();
		String orderId = order(org, "BUSINESS");
		fixtures.webhook(BillingFixtures.paymentEvent(BillingFixtures.eventId(), "PAYMENT_RECEIVED", orderId, 1))
			.andExpect(status().isOk());
		assertThat(fixtures.subscription(fixtures.tenant(org).tenantId()).planTier()).isEqualTo("BUSINESS");
	}

	private String order(String org, String plan) throws Exception {
		return fixtures.order(BillingFixtures.bearer(org, "user_owner", "admin"), plan).get("orderId").asString();
	}

}
