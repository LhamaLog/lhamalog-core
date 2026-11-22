package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingAsaasWebhookSender;
import tools.jackson.databind.JsonNode;

/** Spec 010, US1/US4: the Asaas simulator page (local profile, which is the test profile). */
@CoreIntegrationTest
class FakeAsaasCheckoutIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AsaasWebhookCredential credential;

	@Autowired
	private RecordingAsaasWebhookSender sender;

	private BillingFixtures fixtures;

	@BeforeEach
	void setUp() {
		fixtures = new BillingFixtures(mockMvc, mongo, credential);
		fixtures.clean();
		sender.reset();
	}

	@Test
	void pageShowsTheOrderWithoutASessionToken() throws Exception {
		String path = checkoutPath(BillingFixtures.newOrg(), "STARTER");
		mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Empresa Teste")))
			.andExpect(content().string(Matchers.containsString("STARTER")))
			.andExpect(content().string(Matchers.containsString("49,90")))
			.andExpect(content().string(Matchers.containsString("Simular pagamento")));
	}

	@Test
	void unknownChargeIsNotFound() throws Exception {
		mockMvc.perform(get("/dev/fake-asaas/checkouts/pay_fake_missing")).andExpect(status().isNotFound());
		mockMvc.perform(post("/dev/fake-asaas/checkouts/pay_fake_missing/pay")).andExpect(status().isNotFound());
		assertThat(sender.sent()).isEmpty();
	}

	@Test
	void simulatedPaymentSendsAnAsaasEventThatActivatesTheTenant() throws Exception {
		String org = BillingFixtures.newOrg();
		String path = checkoutPath(org, "PRO");
		mockMvc.perform(post(path + "/pay"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("http://localhost:5173/empresa")));
		assertThat(sender.sent()).hasSize(1);
		JsonNode event = BillingFixtures.JSON.readTree(sender.sent().getFirst());
		assertThat(event.get("event").asString()).isEqualTo("PAYMENT_RECEIVED");
		assertThat(event.get("id").asString()).startsWith("evt_fake_");
		assertThat(event.get("payment").get("billingType").asString()).isEqualTo("PIX");

		fixtures.webhook(sender.sent().getFirst()).andExpect(status().isOk());
		assertThat(fixtures.tenants(org)).isOne();
		assertThat(fixtures.subscription(fixtures.tenant(org).tenantId()).planTier()).isEqualTo("PRO");
	}

	@Test
	void webhookRefusalIsShownOnThePage() throws Exception {
		sender.answer(500);
		mockMvc.perform(post(checkoutPath(BillingFixtures.newOrg(), "PRO") + "/pay"))
			.andExpect(status().isBadGateway())
			.andExpect(content().string(Matchers.containsString("500")));
	}

	@Test
	void companyNameIsEscapedOnThePage() throws Exception {
		String admin = BillingFixtures.bearer(BillingFixtures.newOrg(), "user_owner", "admin");
		String url = BillingFixtures.JSON
			.readTree(fixtures.checkout(admin, "PRO", "<script>x</script>")
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString())
			.get("paymentUrl")
			.asString();
		mockMvc.perform(get(url.substring(url.indexOf("/dev/"))))
			.andExpect(content().string(Matchers.not(Matchers.containsString("<script>"))))
			.andExpect(content().string(Matchers.containsString("&lt;script&gt;")));
	}

	private String checkoutPath(String org, String plan) throws Exception {
		String url = fixtures.order(BillingFixtures.bearer(org, "user_owner", "admin"), plan).get("paymentUrl").asString();
		return url.substring(url.indexOf("/dev/"));
	}

}
