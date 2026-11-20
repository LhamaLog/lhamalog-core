package br.com.lhamalog.core.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookController;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.out.mongo.GatewayEventDocument;
import br.com.lhamalog.core.billing.adapter.out.mongo.SubscriptionOrderDocument;
import br.com.lhamalog.core.billing.adapter.out.mongo.TenantSubscriptionDocument;
import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.support.TestJwks;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared steps of the purchase tests (spec 010). Each test uses its own organization ids. */
final class BillingFixtures {

	static final JsonMapper JSON = JsonMapper.builder().build();

	private final MockMvc mockMvc;

	private final MongoTemplate mongo;

	private final AsaasWebhookCredential credential;

	BillingFixtures(MockMvc mockMvc, MongoTemplate mongo, AsaasWebhookCredential credential) {
		this.mockMvc = mockMvc;
		this.mongo = mongo;
		this.credential = credential;
	}

	static String newOrg() {
		return "org_bill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	/** Session token of a user in an organization, whether or not it has a tenant. */
	static String bearer(String organization, String user, String role) {
		return TestJwks.token().organization(organization, role).subject(user).bearer();
	}

	void clean() {
		mongo.remove(new Query(), SubscriptionOrderDocument.class);
		mongo.remove(new Query(), GatewayEventDocument.class);
		mongo.remove(new Query(), TenantSubscriptionDocument.class);
	}

	ResultActions checkout(String authorization, String plan, String companyName) throws Exception {
		String body = JSON.writeValueAsString(JSON.createObjectNode().put("plan", plan).put("companyName", companyName));
		return mockMvc.perform(post("/api/billing/checkout").header(HttpHeaders.AUTHORIZATION, authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** Creates an order and returns the response body. */
	JsonNode order(String authorization, String plan) throws Exception {
		String response = checkout(authorization, plan, "Empresa Teste").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(response);
	}

	ResultActions latestOrder(String authorization) throws Exception {
		return mockMvc.perform(get("/api/billing/order").header(HttpHeaders.AUTHORIZATION, authorization));
	}

	ResultActions webhook(String body) throws Exception {
		return webhook(body, credential.value());
	}

	ResultActions webhook(String body, String token) throws Exception {
		var request = post(AsaasWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			request.header(AsaasWebhookController.TOKEN_HEADER, token);
		}
		return mockMvc.perform(request);
	}

	/** Asaas payment event for an order. */
	static String paymentEvent(String eventId, String type, String orderId, Object value) {
		var root = JSON.createObjectNode();
		root.put("id", eventId);
		root.put("event", type);
		root.put("dateCreated", "2026-11-18 10:00:00");
		var payment = root.putObject("payment");
		payment.put("object", "payment");
		payment.put("id", "pay_test_" + eventId);
		payment.put("customer", "cus_test");
		if (value instanceof Number number) {
			payment.put("value", new java.math.BigDecimal(number.toString()));
		}
		payment.put("status", "RECEIVED");
		payment.put("billingType", "PIX");
		if (orderId != null) {
			payment.put("externalReference", orderId);
		}
		payment.put("someFieldAsaasAddsLater", "ignored");
		return JSON.writeValueAsString(root);
	}

	static String eventId() {
		return "evt_test_" + UUID.randomUUID().toString().replace("-", "");
	}

	long tenants(String organization) {
		return mongo.count(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(organization)),
				TenantDocument.class);
	}

	TenantDocument tenant(String organization) {
		return mongo.findOne(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(organization)),
				TenantDocument.class);
	}

	long subscriptions(String tenantId) {
		return mongo.count(Query.query(Criteria.where("_id").is(tenantId)), TenantSubscriptionDocument.class);
	}

	TenantSubscriptionDocument subscription(String tenantId) {
		return mongo.findById(tenantId, TenantSubscriptionDocument.class);
	}

	java.util.List<MemberDocument> members(String tenantId) {
		return mongo.find(Query.query(Criteria.where("tenantId").is(tenantId)), MemberDocument.class);
	}

	SubscriptionOrderDocument storedOrder(String orderId) {
		return mongo.findById(orderId, SubscriptionOrderDocument.class);
	}

	GatewayEventDocument event(String eventId) {
		return mongo.findById(eventId, GatewayEventDocument.class);
	}

}
