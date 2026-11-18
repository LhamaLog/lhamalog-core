package br.com.lhamalog.core.ingestiontoken.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import br.com.lhamalog.core.ingestiontoken.application.TokenSecrets;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 010: HTTP flow of the token API with real Clerk tokens and tenants (LLT-163) and MongoDB. */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class IngestionTokenControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	private RequestPostProcessor session(String organization, String user, String role) {
		return tenants().session(organization, user, role);
	}

	/** Real tokens and an ACTIVE tenant per organization (LLT-163). */
	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private String organization() { return "org_test_" + UUID.randomUUID(); }

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode createProduct(RequestPostProcessor admin) throws Exception {
		return json(mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"API de Pagamentos\",\"context\":{}}")).andExpect(status().isCreated()));
	}

	private ResultActions createToken(RequestPostProcessor actor, String productId, String name) throws Exception {
		return mvc.perform(post("/api/products/" + productId + "/tokens").with(actor)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"));
	}

	@Test
	void fullLifecycleShowsTheSecretOnlyOnceAndAuditsEveryOperation(CapturedOutput output) throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		String productId = createProduct(admin).get("id").asText();

		JsonNode created = json(createToken(admin, productId, "cluster-producao").andExpect(status().isCreated()));
		String secret = created.get("secret").asText();
		String tokenId = created.get("token").get("id").asText();
		assertTrue(secret.startsWith("llt_"));
		assertEquals("ACTIVE", created.get("token").get("state").asText());
		assertEquals(TokenResponses.SHOWN_ONCE, created.get("notice").asText());

		Document stored = mongo.findOne(Query.query(Criteria.where("_id").is(tokenId)), Document.class, "product_tokens");
		String hash = stored.getString("tokenHash");
		assertEquals(TokenSecrets.sha256Hex(secret), hash);
		assertEquals(productId, stored.getString("productId"));
		// The internal tenant id (tenant collection), never the Clerk organization id.
		assertEquals(tenants().tenantId(organization), stored.getString("tenantId"));

		String listBody = mvc.perform(get("/api/products/" + productId + "/tokens").with(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		assertFalse(listBody.contains(secret));
		assertFalse(listBody.contains(hash));
		assertFalse(listBody.contains("secret"));

		JsonNode rotation = json(mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/rotate")
				.with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"transitionHours\":2}"))
				.andExpect(status().isCreated()));
		String newSecret = rotation.get("created").get("secret").asText();
		String newId = rotation.get("created").get("token").get("id").asText();
		assertNotEquals(secret, newSecret);
		assertEquals("EXPIRING", rotation.get("previous").get("state").asText());
		assertEquals(newId, rotation.get("previous").get("replacedBy").asText());
		assertFalse(rotation.get("previous").get("expiresAt").isNull());

		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/revoke").with(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.state").value("REVOKED"))
				.andExpect(jsonPath("$.revokedBy").value("admin_1"));
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/revoke").with(admin))
				.andExpect(status().isOk());

		String auditBody = mvc.perform(get("/api/products/" + productId + "/tokens/audit").with(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[0].action").value("REVOKED"))
				.andReturn().getResponse().getContentAsString();
		List<String> actions = new ArrayList<>();
		mapper.readTree(auditBody).forEach(entry -> actions.add(entry.get("action").asText()));
		assertTrue(actions.containsAll(List.of("CREATED", "ROTATED", "REVOKED")));
		assertFalse(auditBody.contains(secret));
		assertFalse(auditBody.contains(newSecret));
		assertFalse(auditBody.contains(hash));

		// Logs: one business event per operation (principle VI), never the token value or its hash.
		String logs = output.getOut();
		assertTrue(logs.contains("event=ingestion_token.created"));
		assertTrue(logs.contains("event=ingestion_token.rotated"));
		assertTrue(logs.contains("event=ingestion_token.revoked"));
		assertTrue(logs.contains("tokenId=" + tokenId));
		assertFalse(logs.contains(secret));
		assertFalse(logs.contains(newSecret));
		assertFalse(logs.contains(hash));
		assertFalse(logs.contains(TokenSecrets.sha256Hex(newSecret)));
	}

	@Test
	void membersAreForbiddenWithCodeAndOtherCompaniesSeeNothing() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		String productId = createProduct(admin).get("id").asText();
		String tokenId = json(createToken(admin, productId, "cluster").andExpect(status().isCreated()))
				.get("token").get("id").asText();

		// Business 403 carries a code, so the Web keeps the session (spec 008, FR-010).
		var member = session(organization, "member_1", "member");
		mvc.perform(get("/api/products/" + productId + "/tokens").with(member)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Somente donos e administradores podem gerenciar tokens."))
				.andExpect(jsonPath("$.code").value("not_admin"));
		createToken(member, productId, "outro").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/revoke").with(member))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(get("/api/products/" + productId + "/tokens/audit").with(member))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));

		var otherAdmin = session(organization(), "admin_b", "admin");
		mvc.perform(get("/api/products/" + productId + "/tokens").with(otherAdmin)).andExpect(status().isNotFound());
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/revoke").with(otherAdmin))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/products/" + productId + "/tokens/missing/revoke").with(admin))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Token não encontrado."));
	}

	@Test
	void requestsWithoutAValidTokenAreRefusedBeforeTheService() throws Exception {
		mvc.perform(get("/api/products/any/tokens")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/products/any/tokens").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"cluster\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidInputAndArchivedProductAreRefused() throws Exception {
		var admin = session(organization(), "admin_1", "admin");
		JsonNode product = createProduct(admin);
		String productId = product.get("id").asText();
		createToken(admin, productId, "   ").andExpect(status().isBadRequest());
		createToken(admin, productId, "a".repeat(101)).andExpect(status().isBadRequest());
		String tokenId = json(createToken(admin, productId, "cluster").andExpect(status().isCreated()))
				.get("token").get("id").asText();
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/rotate").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"transitionHours\":0}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/rotate").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"transitionHours\":169}"))
				.andExpect(status().isBadRequest());

		mvc.perform(post("/api/products/" + productId + "/archive").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":" + product.get("version").asLong() + "}")).andExpect(status().isOk());
		createToken(admin, productId, "novo").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Produto arquivado não pode receber tokens novos."));
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/rotate").with(admin))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/products/" + productId + "/tokens/" + tokenId + "/revoke").with(admin))
				.andExpect(status().isOk());
	}
}
