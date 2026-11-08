package br.com.lhamalog.core.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 002: edit general data, archive and unarchive products (LLT-123). */
@SpringBootTest(properties = {
		"spring.mongodb.uri=${TEST_MONGODB_URI}",
		"management.otlp.metrics.export.enabled=false", "management.tracing.export.otlp.enabled=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class ProductEditArchiveIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	@Autowired ProductRepository products;

	private RequestPostProcessor session(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", organization, "rol", role)));
	}

	private String organization() { return "org_test_" + UUID.randomUUID(); }

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode create(RequestPostProcessor actor, String name) throws Exception {
		return json(mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"context\":{\"problemSolved\":\"Contexto original\"}}"))
				.andExpect(status().isCreated()));
	}

	private String general(String name, String description, long version) {
		return "{\"name\":\"" + name + "\",\"description\":\"" + description
				+ "\",\"category\":\"Pagamentos\",\"productVersion\":\"2.0\",\"version\":" + version + "}";
	}

	private ResultActions edit(RequestPostProcessor actor, String id, String body) throws Exception {
		return mvc.perform(put("/api/products/" + id).with(actor).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions changeArchive(RequestPostProcessor actor, String id, String action, long version)
			throws Exception {
		return mvc.perform(post("/api/products/" + id + "/" + action).with(actor)
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"));
	}

	private static boolean nullOrMissing(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull();
	}

	// US1 — editar dados gerais

	@Test
	void editPersistsGeneralDataAndKeepsContextAndStatus() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();

		edit(creator, id, general("  Produto B  ", "Nova descrição", created.get("version").asLong()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Produto B"));

		mvc.perform(get("/api/products/" + id).with(creator)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Produto B"))
				.andExpect(jsonPath("$.description").value("Nova descrição"))
				.andExpect(jsonPath("$.category").value("Pagamentos"))
				.andExpect(jsonPath("$.productVersion").value("2.0"))
				.andExpect(jsonPath("$.context.problemSolved").value("Contexto original"))
				.andExpect(jsonPath("$.status").value("CONFIGURING"));
		assertEquals("Produto B", products.findById(id).orElseThrow().name());
	}

	@Test
	void editRejectsStaleVersionAndKeepsFirstSave() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		edit(creator, id, general("Primeira", "", version)).andExpect(status().isOk());
		edit(creator, id, general("Segunda", "", version)).andExpect(status().isConflict());
		assertEquals("Primeira", products.findById(id).orElseThrow().name());
	}

	@Test
	void editValidatesFieldsWithoutChangingProduct() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		edit(creator, id, general("   ", "", version))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
		edit(creator, id, general("a".repeat(201), "", version))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
		edit(creator, id, general("Produto", "d".repeat(10001), version))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.description").exists());
		edit(creator, id, "{\"name\":\"Produto\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.version").exists());
		assertEquals("Produto A", products.findById(id).orElseThrow().name());

		edit(creator, id, general("Produto", "d".repeat(10000), version)).andExpect(status().isOk());
	}

	@Test
	void editRespectsProductAccessAndCompanyIsolation() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		edit(session(tenant, "other", "member"), id, general("Invasor", "", version)).andExpect(status().isNotFound());
		edit(session(organization(), "creator", "admin"), id, general("Invasor", "", version))
				.andExpect(status().isNotFound());
		assertEquals("Produto A", products.findById(id).orElseThrow().name());

		edit(session(tenant, "admin", "admin"), id, general("Pelo admin", "", version)).andExpect(status().isOk());
	}

	// US2 — arquivar

	@Test
	void archiveHidesProductKeepsDataAndBlocksEdits() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();

		JsonNode archived = json(changeArchive(admin, id, "archive", created.get("version").asLong())
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIGURING")));
		assertFalse(nullOrMissing(archived, "archivedAt"));

		mvc.perform(get("/api/products").with(creator)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/products").with(admin)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/products/" + id).with(creator)).andExpect(status().isOk())
				.andExpect(jsonPath("$.context.problemSolved").value("Contexto original"));

		long version = archived.get("version").asLong();
		edit(creator, id, general("Mudou", "", version)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Produto arquivado não pode ser editado."));
		mvc.perform(put("/api/products/" + id + "/context").with(creator).contentType(MediaType.APPLICATION_JSON)
				.content("{\"context\":{\"problemSolved\":\"Mudou\"},\"version\":" + version + "}"))
				.andExpect(status().isConflict());
		Product stored = products.findById(id).orElseThrow();
		assertEquals("Produto A", stored.name());
		assertEquals("Contexto original", stored.context().problemSolved());
	}

	@Test
	void archiveRequiresAdministratorAndIsIsolated() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		changeArchive(creator, id, "archive", version).andExpect(status().isForbidden());
		changeArchive(session(tenant, "other", "member"), id, "archive", version).andExpect(status().isNotFound());
		changeArchive(session(organization(), "admin", "admin"), id, "archive", version)
				.andExpect(status().isNotFound());
		assertNull(products.findById(id).orElseThrow().archivedAt());
		mvc.perform(get("/api/products").with(creator)).andExpect(jsonPath("$[0].id").value(id));
	}

	@Test
	void archiveTwiceKeepsOriginalDateAndStaleVersionIsRejected() throws Exception {
		String tenant = organization();
		var admin = session(tenant, "admin", "admin");
		JsonNode created = create(session(tenant, "creator", "member"), "Produto A");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		edit(admin, id, general("Produto A2", "", version)).andExpect(status().isOk());
		changeArchive(admin, id, "archive", version).andExpect(status().isConflict());

		long current = products.findById(id).orElseThrow().version();
		JsonNode first = json(changeArchive(admin, id, "archive", current).andExpect(status().isOk()));
		JsonNode second = json(changeArchive(admin, id, "archive", first.get("version").asLong())
				.andExpect(status().isOk()));
		assertEquals(first.get("archivedAt").asText(), second.get("archivedAt").asText());
		assertEquals(first.get("version").asLong(), second.get("version").asLong());
	}

	// US3 — filtrar arquivados e desarquivar

	@Test
	void archivedFilterShowsOnlyArchivedProductsVisibleToTheUser() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		JsonNode toArchive = create(creator, "Arquivado");
		create(creator, "Ativo");
		JsonNode hidden = create(admin, "Arquivado do admin");
		changeArchive(admin, toArchive.get("id").asText(), "archive", toArchive.get("version").asLong())
				.andExpect(status().isOk());
		changeArchive(admin, hidden.get("id").asText(), "archive", hidden.get("version").asLong())
				.andExpect(status().isOk());

		mvc.perform(get("/api/products").with(creator))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Ativo"));
		mvc.perform(get("/api/products").param("archived", "true").with(creator))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Arquivado"));
		mvc.perform(get("/api/products").param("archived", "true").with(admin))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/products").param("archived", "true").with(session(organization(), "admin", "admin")))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unarchiveRestoresListingAndEditing() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		JsonNode created = create(creator, "Produto A");
		String id = created.get("id").asText();
		JsonNode archived = json(changeArchive(admin, id, "archive", created.get("version").asLong())
				.andExpect(status().isOk()));
		long version = archived.get("version").asLong();

		changeArchive(creator, id, "unarchive", version).andExpect(status().isForbidden());
		changeArchive(session(organization(), "admin", "admin"), id, "unarchive", version)
				.andExpect(status().isNotFound());

		JsonNode restored = json(changeArchive(admin, id, "unarchive", version).andExpect(status().isOk()));
		assertTrue(nullOrMissing(restored, "archivedAt"));
		mvc.perform(get("/api/products").with(creator)).andExpect(jsonPath("$[0].id").value(id));
		mvc.perform(get("/api/products/" + id).with(creator))
				.andExpect(jsonPath("$.context.problemSolved").value("Contexto original"));

		JsonNode again = json(changeArchive(admin, id, "unarchive", restored.get("version").asLong())
				.andExpect(status().isOk()));
		assertEquals(restored.get("version").asLong(), again.get("version").asLong());

		edit(creator, id, general("Editado de novo", "", restored.get("version").asLong())).andExpect(status().isOk());
	}

	@Test
	void archiveEndpointsRequireVersion() throws Exception {
		String tenant = organization();
		var admin = session(tenant, "admin", "admin");
		String id = create(admin, "Produto A").get("id").asText();
		mvc.perform(post("/api/products/" + id + "/archive").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.version").exists());
	}
}
