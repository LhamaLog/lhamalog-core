package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookParser;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookParser.InvalidAsaasPayloadException;
import br.com.lhamalog.core.billing.adapter.out.fake.AsaasWebhookSender;
import br.com.lhamalog.core.billing.application.PlanCatalog;
import br.com.lhamalog.core.billing.domain.GatewayNotification;
import br.com.lhamalog.core.member.domain.Member;
import br.com.lhamalog.core.member.domain.MemberRole;
import br.com.lhamalog.core.usage.application.PlanTier;

/** Spec 010: catalog, credential, parser, simulator sender and owner member. */
class BillingUnitTests {

	// Catalog (research R10)

	@Test
	void catalogOffersOnlyPricedTiersInTierOrder() {
		Map<PlanTier, BigDecimal> prices = new EnumMap<>(PlanTier.class);
		prices.put(PlanTier.PRO, new BigDecimal("149.9"));
		prices.put(PlanTier.FREE, BigDecimal.ZERO);
		PlanCatalog catalog = new PlanCatalog(prices);
		assertThat(catalog.plans()).extracting((plan) -> plan.tier()).containsExactly(PlanTier.FREE, PlanTier.PRO);
		assertThat(catalog.plans().get(1).price()).isEqualByComparingTo("149.90");
		assertThat(catalog.plans().get(1).name()).isEqualTo("Pro");
		assertThat(catalog.find(" pro ")).isPresent();
		assertThat(catalog.find("STARTER")).isEmpty();
		assertThat(catalog.find(null)).isEmpty();
	}

	// Credential (research R8)

	@Test
	void localCredentialIsGeneratedWhenBlankAndMatchesOnlyItself() {
		AsaasWebhookCredential credential = AsaasWebhookCredential.generatedWhenBlank("");
		assertThat(credential.value()).hasSizeGreaterThanOrEqualTo(32);
		assertThat(credential.matches(credential.value())).isTrue();
		assertThat(credential.matches(credential.value() + "x")).isFalse();
		assertThat(credential.matches(null)).isFalse();
		assertThat(AsaasWebhookCredential.generatedWhenBlank(null).value()).isNotEqualTo(credential.value());
		assertThat(credential.toString()).doesNotContain(credential.value());
	}

	@Test
	void realEnvironmentRequiresAValidToken() {
		assertThatThrownBy(() -> AsaasWebhookCredential.configured(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> AsaasWebhookCredential.configured(" ")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> AsaasWebhookCredential.configured("short-token"))
			.isInstanceOf(IllegalStateException.class)
			.message()
			.doesNotContain("short-token");
		assertThatThrownBy(() -> AsaasWebhookCredential.configured("a".repeat(20) + " " + "b".repeat(20)))
			.isInstanceOf(IllegalStateException.class);
		String token = "t".repeat(40);
		assertThat(AsaasWebhookCredential.configured(token).matches(token)).isTrue();
	}

	// Parser (research R1)

	private final AsaasWebhookParser parser = new AsaasWebhookParser();

	@Test
	void parsesTheDocumentedAsaasFormatAndIgnoresUnknownFields() {
		GatewayNotification notification = parse("""
				{"id":"evt_05b708f961d739ea7eba7e4db318f621&368604920","event":"PAYMENT_RECEIVED",
				 "dateCreated":"2024-06-12 16:45:03","newTopLevel":{"a":1},
				 "payment":{"object":"payment","id":"pay_080225913252","customer":"cus_G7Dvo4iphUNk",
				            "subscription":"sub_VXJBYgP2u0eO","value":100,"netValue":97.01,"status":"RECEIVED",
				            "billingType":"CREDIT_CARD","externalReference":"056984"}}
				""");
		assertThat(notification.eventId()).isEqualTo("evt_05b708f961d739ea7eba7e4db318f621&368604920");
		assertThat(notification.gateway()).isEqualTo("ASAAS");
		assertThat(notification.type()).isEqualTo("PAYMENT_RECEIVED");
		assertThat(notification.chargeId()).isEqualTo("pay_080225913252");
		assertThat(notification.orderId()).isEqualTo("056984");
		assertThat(notification.value()).isEqualByComparingTo("100");
		assertThat(notification.confirmsPayment()).isTrue();
	}

	@Test
	void eventWithoutPaymentIsReadToo() {
		GatewayNotification notification = parse("{\"id\":\"evt_1\",\"event\":\"SUBSCRIPTION_CREATED\"}");
		assertThat(notification.orderId()).isNull();
		assertThat(notification.confirmsPayment()).isFalse();
	}

	@Test
	void bodyWithoutIdOrEventIsInvalid() {
		assertThatThrownBy(() -> parse("{\"event\":\"PAYMENT_RECEIVED\"}"))
			.isInstanceOf(InvalidAsaasPayloadException.class);
		assertThatThrownBy(() -> parse("{\"id\":\"evt_1\",\"event\":\"\"}"))
			.isInstanceOf(InvalidAsaasPayloadException.class);
		assertThatThrownBy(() -> parse("[]")).isInstanceOf(InvalidAsaasPayloadException.class);
		assertThatThrownBy(() -> parse("{")).isInstanceOf(InvalidAsaasPayloadException.class);
		assertThatThrownBy(() -> parser.parse(new byte[0])).isInstanceOf(InvalidAsaasPayloadException.class);
	}

	private GatewayNotification parse(String json) {
		return parser.parse(json.getBytes(StandardCharsets.UTF_8));
	}

	// Simulator sender (research R9)

	@Test
	void senderPostsTheEventWithTheTokenAndReportsTheStatus() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AsaasWebhookCredential credential = AsaasWebhookCredential.generatedWhenBlank(null);
		AsaasWebhookSender sender = new AsaasWebhookSender(builder, "http://localhost:8080/webhooks/asaas", credential);
		server.expect(requestTo("http://localhost:8080/webhooks/asaas"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("asaas-access-token", credential.value()))
			.andExpect(content().json("{\"event\":\"PAYMENT_RECEIVED\"}"))
			.andRespond(withSuccess());
		server.expect(requestTo("http://localhost:8080/webhooks/asaas"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertThat(sender.send("{\"event\":\"PAYMENT_RECEIVED\"}")).isEqualTo(200);
		assertThat(sender.send("{\"event\":\"PAYMENT_RECEIVED\"}")).isEqualTo(401);
		server.verify();
	}

	@Test
	void unreachableWebhookIsReported() {
		AsaasWebhookSender sender = new AsaasWebhookSender(RestClient.builder(), "http://127.0.0.1:1/webhooks/asaas",
				AsaasWebhookCredential.generatedWhenBlank(null));
		assertThat(sender.send("{}")).isEqualTo(AsaasWebhookSender.UNREACHABLE);
	}

	// Owner member (research R11)

	@Test
	void ownerStartsWithoutProfileAndKeepsTheRoleOnClerkSync() {
		Member owner = Member.owner("m1", "t1", "u1");
		assertThat(owner.role()).isEqualTo(MemberRole.DONO);
		assertThat(owner.membershipVersion()).isZero();
		assertThat(owner.name()).isNull();
		assertThat(owner.role().syncedTo(MemberRole.MEMBRO)).isEqualTo(MemberRole.DONO);
	}

	@Test
	void promotionChangesOnlyTheRole() {
		Member admin = new Member("m1", "t1", "u1", "orgmem_1", MemberRole.ADMIN, "Nome", "n@x.test", 5, 3, null, null);
		Member owner = admin.promotedToOwner();
		assertThat(owner.role()).isEqualTo(MemberRole.DONO);
		assertThat(owner).usingRecursiveComparison().ignoringFields("role").isEqualTo(admin);
		assertThat(owner.promotedToOwner()).isSameAs(owner);
	}

}
