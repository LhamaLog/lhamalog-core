package br.com.lhamalog.core.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import br.com.lhamalog.core.ingestiontoken.application.TokenSecrets;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Feature 013: products are stored where the Collector reads them ({@code products.productId}, LLT-181),
 * so an ingestion token created by the Core is accepted end to end (LLT-179, D1 = B).
 */
@CoreIntegrationTest
class ProductCollectorContractIntegrationTests {
	private static final String PRODUCTS = "products";
	private static final String OLD_COLLECTION = "Produto";
	private static final String TOKENS = "product_tokens";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	/** Real tokens and an ACTIVE tenant per organization (LLT-163). */
	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private RequestPostProcessor admin(String organization) {
		return tenants().session(organization, "admin_1", "admin");
	}

	private String organization() { return "org_test_" + UUID.randomUUID(); }

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private JsonNode createProduct(RequestPostProcessor actor) throws Exception {
		return json(mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Gateway de Pagamentos\",\"context\":{}}")).andExpect(status().isCreated()));
	}

	private Document stored(String productId) {
		return mongo.findOne(Query.query(Criteria.where("_id").is(productId)), Document.class, PRODUCTS);
	}

	private void assertCollectorFields(String productId, String organization) {
		Document product = stored(productId);
		assertNotNull(product, "product must be in the products collection");
		assertEquals(productId, product.getString("productId"));
		assertEquals(tenants().tenantId(organization), product.getString("tenantId"));
	}

	@Test
	void createdProductIsStoredInProductsWithProductIdAndNothingInTheOldCollection() throws Exception {
		String organization = organization();
		String productId = createProduct(admin(organization)).get("id").asText();

		assertCollectorFields(productId, organization);
		assertEquals(0, mongo.count(Query.query(Criteria.where("_id").is(productId)), OLD_COLLECTION));
	}

	@Test
	void everyLaterWriteKeepsTheProductId() throws Exception {
		String organization = organization();
		var admin = admin(organization);
		JsonNode created = createProduct(admin);
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		JsonNode edited = json(mvc.perform(put("/api/products/" + id).with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Gateway de Pagamentos 2\",\"description\":\"d\",\"category\":\"Pagamentos\","
						+ "\"productVersion\":\"2.0\",\"version\":" + version + "}"))
				.andExpect(status().isOk()));
		assertCollectorFields(id, organization);

		JsonNode withContext = json(mvc.perform(put("/api/products/" + id + "/context").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"context\":{\"functionalDescription\":\"Recebe pagamentos.\"},\"version\":"
						+ edited.get("version").asLong() + "}"))
				.andExpect(status().isOk()));
		assertCollectorFields(id, organization);

		JsonNode archived = json(mvc.perform(post("/api/products/" + id + "/archive").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + withContext.get("version").asLong() + "}"))
				.andExpect(status().isOk()));
		assertCollectorFields(id, organization);

		mvc.perform(post("/api/products/" + id + "/unarchive").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":" + archived.get("version").asLong() + "}")).andExpect(status().isOk());
		assertCollectorFields(id, organization);
	}

	@Test
	void tokenCreatedByTheCorePassesTheCollectorLookup() throws Exception {
		String organization = organization();
		var admin = admin(organization);
		String productId = createProduct(admin).get("id").asText();
		String secret = json(mvc.perform(post("/api/products/" + productId + "/tokens").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cluster-producao\"}"))
				.andExpect(status().isCreated())).get("secret").asText();

		// Same two reads as the Collector's MongoProductTokenStore.lookup.
		Document token = mongo.findOne(Query.query(Criteria.where("tokenHash").is(TokenSecrets.sha256Hex(secret))),
				Document.class, TOKENS);
		assertNotNull(token);
		assertEquals("ACTIVE", token.getString("status"));
		Document product = mongo.findOne(Query.query(Criteria.where("productId").is(token.getString("productId"))),
				Document.class, PRODUCTS);
		assertNotNull(product, "the Collector must find the product of the token");
		assertEquals(tenants().tenantId(organization), product.getString("tenantId"));
	}

	@Test
	void productIdHasAUniquePartialIndex() {
		Document index = mongo.getCollection(PRODUCTS).listIndexes().into(new java.util.ArrayList<>()).stream()
				.filter(candidate -> "product_collector_product_id".equals(candidate.getString("name")))
				.findFirst().orElse(null);
		assertNotNull(index, "index product_collector_product_id must exist");
		assertEquals(new Document("productId", 1), index.get("key", Document.class));
		assertEquals(Boolean.TRUE, index.getBoolean("unique"));
		assertNotNull(index.get("partialFilterExpression"), "index must be partial");
	}
}
