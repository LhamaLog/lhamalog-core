package br.com.lhamalog.core.access;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookController;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingSessionRevocation;
import br.com.lhamalog.core.support.TestJwks;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 011 (LLT-120), T040: the owner created by the purchase flow of LLT-289 (spec 010, FR-009) is the owner this
 * feature reads. The purchase runs end to end (checkout and the Asaas payment webhook), with no owner written by hand.
 * The billing helpers ({@code BillingFixtures}) are package-private, so the two calls used here are repeated below
 * instead of opening that class.
 */
@CoreIntegrationTest
class AccessPurchaseOwnerIntegrationTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String BUYER = "user_buyer";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AsaasWebhookCredential credential;

	@Autowired
	private RecordingSessionRevocation revocation;

	@BeforeEach
	void setUp() {
		revocation.reset();
	}

	@Test
	void buyerIsTheOwnerOfTheCompanyCreatedByThePayment() throws Exception {
		String org = organization();
		String buyer = bearer(org, BUYER, "admin");
		purchase(org, buyer);

		mvc.perform(get("/api/company/access/me").header(HttpHeaders.AUTHORIZATION, buyer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(BUYER))
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andExpect(jsonPath("$.permissions").value(hasItem("SUBSCRIPTION_MANAGE")));
		mvc.perform(get("/api/company/access").header(HttpHeaders.AUTHORIZATION, bearer(org, "user_admin_b", "admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ownerUserId").value(BUYER));
	}

	@Test
	void anotherAdminCannotDeactivateTheBuyer() throws Exception {
		String org = organization();
		String buyer = bearer(org, BUYER, "admin");
		String otherAdmin = bearer(org, "user_admin_b", "admin");
		purchase(org, buyer);
		// Both are seen by the Core first, as in the LLT-168 tests.
		mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, buyer)).andExpect(status().isOk());
		mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, otherAdmin)).andExpect(status().isOk());

		mvc.perform(post("/api/company/members/" + BUYER + "/deactivation").header(HttpHeaders.AUTHORIZATION, otherAdmin))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("owner_protected"));
		mvc.perform(get("/api/company/members").header(HttpHeaders.AUTHORIZATION, otherAdmin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members[?(@.userId=='" + BUYER + "')].status").value(hasItem("active")));
	}

	@Test
	void buyerHasAccessToAllProducts() throws Exception {
		String org = organization();
		String buyer = bearer(org, BUYER, "admin");
		purchase(org, buyer);
		String otherAdmin = bearer(org, "user_admin_b", "admin");

		mvc.perform(put("/api/company/members/" + BUYER + "/products").header(HttpHeaders.AUTHORIZATION, otherAdmin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productIds\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("full_access"));
		mvc.perform(get("/api/company/members/" + BUYER + "/products").header(HttpHeaders.AUTHORIZATION, otherAdmin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allProducts").value(true));
	}

	@Test
	void organizationWithoutTenantGetsSubscriptionRequiredOnTheNewRoutesAndKeepsItsSession() throws Exception {
		String org = organization();
		String admin = bearer(org, "user_no_tenant", "admin");

		for (String path : new String[] { "/api/company/access/me", "/api/company/access",
				"/api/company/notification-roles", "/api/company/members/user_x/products",
				"/api/company/members/user_x/notification-roles" }) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("subscription_required"));
		}
		assertTrue(revocation.revokedUsers().isEmpty(), "no tenant must not sign the user out (spec 010, FR-006)");
		assertEquals(0, mongo.count(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(org)),
				TenantDocument.class), "the new routes must not create a tenant");
	}

	/** Order and payment confirmation of LLT-289: creates the tenant, the subscription and the DONO member. */
	private void purchase(String org, String authorization) throws Exception {
		String order = mvc
			.perform(post("/api/billing/checkout").header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"STARTER\",\"companyName\":\"Empresa da compra\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String orderId = JSON.readTree(order).get("orderId").asString();

		mvc.perform(post(AsaasWebhookController.PATH).header(AsaasWebhookController.TOKEN_HEADER, credential.value())
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentReceived(orderId)))
			.andExpect(status().isOk());
		assertEquals(1, mongo.count(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(org)),
				TenantDocument.class), "the payment must have created the tenant");
	}

	private static String paymentReceived(String orderId) {
		String eventId = "evt_access_" + UUID.randomUUID().toString().replace("-", "");
		var root = JSON.createObjectNode();
		root.put("id", eventId);
		root.put("event", "PAYMENT_RECEIVED");
		root.put("dateCreated", "2026-11-22 10:00:00");
		var payment = root.putObject("payment");
		payment.put("object", "payment");
		payment.put("id", "pay_" + eventId);
		payment.put("customer", "cus_test");
		payment.put("value", new java.math.BigDecimal("49.9"));
		payment.put("status", "RECEIVED");
		payment.put("billingType", "PIX");
		payment.put("externalReference", orderId);
		return JSON.writeValueAsString(root);
	}

	private static String bearer(String organization, String user, String role) {
		return TestJwks.token().organization(organization, role).subject(user).bearer();
	}

	private static String organization() {
		return "org_access_buy_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

}
