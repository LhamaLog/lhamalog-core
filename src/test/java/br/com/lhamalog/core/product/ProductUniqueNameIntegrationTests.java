package br.com.lhamalog.core.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 006: product name unique per company (LLT-177). */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProductUniqueNameIntegrationTests {
	private static final String NAME_IN_USE = "Já existe um produto com este nome nesta empresa. Ele pode estar arquivado.";
	private static final String STALE_VERSION = "Os dados foram alterados. Recarregue antes de salvar novamente.";
	private static final String ARCHIVED = "Produto arquivado não pode ser editado.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	@Autowired ProductRepository products;
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
		return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private ResultActions attemptCreate(RequestPostProcessor actor, String name) throws Exception {
		return mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"context\":{\"functionalDescription\":\"Contexto original\"}}"));
	}

	private JsonNode create(RequestPostProcessor actor, String name) throws Exception {
		return json(attemptCreate(actor, name).andExpect(status().isCreated()));
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

	private void expectNameInUse(ResultActions result) throws Exception {
		result.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(NAME_IN_USE))
				.andExpect(jsonPath("$.fieldErrors").isEmpty()).andExpect(jsonPath("$.id").doesNotExist());
	}

	private long storedProducts(String tenant) {
		return mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(tenant))), Product.class);
	}

	private long storedAccesses(String tenant) {
		return mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(tenant))), ProductAccess.class);
	}

	/** Releases all requests at once and returns their responses, in submission order. */
	private List<MockHttpServletResponse> inParallel(List<Callable<ResultActions>> requests) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(requests.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<ResultActions>> pending = new ArrayList<>();
			for (Callable<ResultActions> request : requests) {
				pending.add(pool.submit(() -> {
					start.await();
					return request.call();
				}));
			}
			start.countDown();
			List<MockHttpServletResponse> responses = new ArrayList<>();
			for (Future<ResultActions> result : pending) {
				responses.add(result.get(60, TimeUnit.SECONDS).andReturn().getResponse());
			}
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private void expectOneAcceptedAndOthersNameInUse(List<MockHttpServletResponse> responses, int acceptedStatus)
			throws Exception {
		assertEquals(1, responses.stream().filter(response -> response.getStatus() == acceptedStatus).count());
		for (MockHttpServletResponse response : responses) {
			if (response.getStatus() == acceptedStatus) continue;
			assertEquals(409, response.getStatus());
			assertEquals(NAME_IN_USE,
					mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("message").asText());
		}
	}

	// US1: reject a repeated name on create

	@Test
	void createRejectsNameAlreadyUsedInCompanyAndLeavesNothingBehind() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		create(actor, "Checkout");

		expectNameInUse(attemptCreate(actor, "Checkout"));

		assertEquals(1, storedProducts(tenant));
		assertEquals(1, storedAccesses(tenant));
	}

	@Test
	void createRejectsCaseAndSurroundingSpaceVariations() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		create(actor, "Checkout");

		expectNameInUse(attemptCreate(actor, "checkout"));
		expectNameInUse(attemptCreate(actor, "CHECKOUT"));
		expectNameInUse(attemptCreate(actor, "  Checkout  "));

		assertEquals(1, storedProducts(tenant));
	}

	@Test
	void createRejectsNameOfArchivedProduct() throws Exception {
		String tenant = organization();
		var admin = session(tenant, "admin", "admin");
		JsonNode occupant = create(admin, "Checkout");
		changeArchive(admin, occupant.get("id").asText(), "archive", occupant.get("version").asLong())
				.andExpect(status().isOk());

		expectNameInUse(attemptCreate(admin, "checkout"));

		assertEquals(1, storedProducts(tenant));
	}

	@Test
	void createAcceptsNameUsedOnlyByAnotherCompany() throws Exception {
		create(session(organization(), "creator", "member"), "Checkout");

		create(session(organization(), "creator", "member"), "Checkout");
	}

	@Test
	void createStoresTrimmedNameAndHidesNormalizedForm() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		String id = json(attemptCreate(actor, "  Meu Produto  ").andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Meu Produto"))
				.andExpect(jsonPath("$.normalizedName").doesNotExist())).get("id").asText();

		Product stored = products.findById(id).orElseThrow();
		assertEquals("Meu Produto", stored.name());
		assertEquals("meu produto", stored.normalizedName());
		mvc.perform(get("/api/products/" + id).with(actor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.normalizedName").doesNotExist());
		mvc.perform(get("/api/products").with(actor)).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].normalizedName").doesNotExist());
	}

	@Test
	void concurrentCreatesWithTheSameNameAcceptExactlyOne() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		List<Callable<ResultActions>> requests = Collections.nCopies(8, () -> attemptCreate(actor, "Checkout"));

		List<MockHttpServletResponse> responses = inParallel(requests);

		expectOneAcceptedAndOthersNameInUse(responses, 201);
		assertEquals(1, storedProducts(tenant));
		assertEquals(1, storedAccesses(tenant));
	}

	@Test
	void createFoldsAccentedCaseButKeepsAccentsAndInnerSpacesDistinct() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		create(actor, "AÇÃO");

		expectNameInUse(attemptCreate(actor, "ação"));
		create(actor, "Acao");
		create(actor, "Meu App");
		create(actor, "Meu  App");

		assertEquals(4, storedProducts(tenant));
	}

	@Test
	void invalidNameIsRejectedAsValidationEvenWhenNameIsTaken() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "creator", "member");
		String longest = "a".repeat(200);
		create(actor, longest);

		attemptCreate(actor, "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
		// 201 characters that trim down to a name already in use: the size limit applies first.
		attemptCreate(actor, longest + " ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		assertEquals(1, storedProducts(tenant));
	}

	// US2: reject a repeated name on edit

	@Test
	void editRejectsNameOfAnotherProductAndKeepsEverything() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		create(creator, "Checkout");
		JsonNode target = create(creator, "Pagamentos");
		String id = target.get("id").asText();
		long version = target.get("version").asLong();

		expectNameInUse(edit(creator, id, general(" checkout ", "Nova descrição", version)));

		Product stored = products.findById(id).orElseThrow();
		assertEquals("Pagamentos", stored.name());
		assertEquals("pagamentos", stored.normalizedName());
		assertNull(stored.description());
		assertNull(stored.category());
		assertEquals(version, stored.version());
	}

	@Test
	void editRejectsNameOfArchivedProduct() throws Exception {
		String tenant = organization();
		var admin = session(tenant, "admin", "admin");
		JsonNode occupant = create(admin, "Checkout");
		changeArchive(admin, occupant.get("id").asText(), "archive", occupant.get("version").asLong())
				.andExpect(status().isOk());
		JsonNode target = create(admin, "Pagamentos");
		String id = target.get("id").asText();

		expectNameInUse(edit(admin, id, general("Checkout", "", target.get("version").asLong())));

		assertEquals("Pagamentos", products.findById(id).orElseThrow().name());
	}

	@Test
	void editAcceptsOwnNameAndStoresNewCaseAndTrimmedForm() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Checkout");
		String id = created.get("id").asText();

		JsonNode same = json(edit(creator, id, general("Checkout", "Outra descrição", created.get("version").asLong()))
				.andExpect(status().isOk()));
		edit(creator, id, general("  CHECKOUT  ", "", same.get("version").asLong())).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("CHECKOUT"))
				.andExpect(jsonPath("$.normalizedName").doesNotExist());

		Product stored = products.findById(id).orElseThrow();
		assertEquals("CHECKOUT", stored.name());
		assertEquals("checkout", stored.normalizedName());
	}

	@Test
	void renameFreesTheOldName() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		JsonNode created = create(creator, "Pagamentos");

		edit(creator, created.get("id").asText(), general("Cobranças", "", created.get("version").asLong()))
				.andExpect(status().isOk());

		create(creator, "Pagamentos");
		assertEquals(2, storedProducts(tenant));
	}

	@Test
	void editWithTakenNameStillAnswersNotFoundWithoutAccessOrFromAnotherCompany() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		create(creator, "Checkout");
		JsonNode target = create(creator, "Pagamentos");
		String id = target.get("id").asText();
		long version = target.get("version").asLong();

		edit(session(tenant, "other", "member"), id, general("Checkout", "", version))
				.andExpect(status().isNotFound());
		edit(session(organization(), "creator", "admin"), id, general("Checkout", "", version))
				.andExpect(status().isNotFound());

		assertEquals("Pagamentos", products.findById(id).orElseThrow().name());
	}

	@Test
	void concurrentRenamesToTheSameNameAcceptExactlyOne() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		List<Callable<ResultActions>> requests = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			JsonNode product = create(creator, "Produto " + index);
			String id = product.get("id").asText();
			long version = product.get("version").asLong();
			requests.add(() -> edit(creator, id, general("Checkout", "", version)));
		}

		List<MockHttpServletResponse> responses = inParallel(requests);

		expectOneAcceptedAndOthersNameInUse(responses, 200);
		assertEquals(1, mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(tenant))
				.and("normalizedName").is("checkout")), Product.class));
	}

	@Test
	void editWithBlankNameIsValidationErrorEvenWhenNamesAreTaken() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		create(creator, "Checkout");
		JsonNode target = create(creator, "Pagamentos");
		String id = target.get("id").asText();

		edit(creator, id, general("   ", "", target.get("version").asLong())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		assertEquals("Pagamentos", products.findById(id).orElseThrow().name());
	}

	@Test
	void staleVersionAndArchivedProductKeepTheirOwnMessagesWhenNameIsTaken() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		create(creator, "Checkout");
		JsonNode target = create(creator, "Pagamentos");
		String id = target.get("id").asText();
		long staleVersion = target.get("version").asLong();
		JsonNode renamed = json(edit(creator, id, general("Pagamentos 2", "", staleVersion))
				.andExpect(status().isOk()));

		edit(creator, id, general("Checkout", "", staleVersion)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(STALE_VERSION));

		JsonNode archived = json(changeArchive(admin, id, "archive", renamed.get("version").asLong())
				.andExpect(status().isOk()));
		edit(creator, id, general("Checkout", "", archived.get("version").asLong())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(ARCHIVED));

		assertEquals("Pagamentos 2", products.findById(id).orElseThrow().name());
	}

	// US3: an archived product keeps its name

	@Test
	void archivedProductKeepsItsNameAndUnarchiveNeverConflicts() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		JsonNode checkout = create(creator, "Checkout");
		String id = checkout.get("id").asText();
		JsonNode other = create(creator, "Pagamentos");

		JsonNode archived = json(changeArchive(admin, id, "archive", checkout.get("version").asLong())
				.andExpect(status().isOk()));
		attemptCreate(creator, "Checkout").andExpect(status().isConflict());
		edit(creator, other.get("id").asText(), general("Checkout", "", other.get("version").asLong()))
				.andExpect(status().isConflict());

		JsonNode restored = json(changeArchive(admin, id, "unarchive", archived.get("version").asLong())
				.andExpect(status().isOk()));
		edit(creator, id, general("Checkout antigo", "", restored.get("version").asLong())).andExpect(status().isOk());

		create(creator, "Checkout");
		assertEquals(3, storedProducts(tenant));
	}

	@Test
	void contextArchiveAndUnarchiveKeepTheNormalizedName() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		var admin = session(tenant, "admin", "admin");
		JsonNode created = create(creator, "Checkout");
		String id = created.get("id").asText();

		JsonNode withContext = json(mvc.perform(put("/api/products/" + id + "/context").with(creator)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"context\":{\"functionalDescription\":\"Novo contexto\"},\"version\":"
						+ created.get("version").asLong() + "}"))
				.andExpect(status().isOk()));
		assertEquals("checkout", products.findById(id).orElseThrow().normalizedName());
		attemptCreate(creator, "Checkout").andExpect(status().isConflict());

		JsonNode archived = json(changeArchive(admin, id, "archive", withContext.get("version").asLong())
				.andExpect(status().isOk()));
		assertEquals("checkout", products.findById(id).orElseThrow().normalizedName());
		attemptCreate(creator, "Checkout").andExpect(status().isConflict());

		changeArchive(admin, id, "unarchive", archived.get("version").asLong()).andExpect(status().isOk());
		assertEquals("checkout", products.findById(id).orElseThrow().normalizedName());
		attemptCreate(creator, "Checkout").andExpect(status().isConflict());

		assertEquals(1, storedProducts(tenant));
	}

	// Constitution VI: the refusal is logged with the event code

	@Test
	void nameInUseRefusalIsLoggedWithEventCodeWithoutNameOrStackTrace(CapturedOutput output) throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		create(creator, "Checkout");
		JsonNode target = create(creator, "Pagamentos");
		String id = target.get("id").asText();

		expectNameInUse(attemptCreate(creator, "checkout"));
		expectNameInUse(edit(creator, id, general("CHECKOUT", "", target.get("version").asLong())));

		String createLine = logLine(output, "event=product.create.name_in_use | ", tenants().tenantId(tenant));
		String updateLine = logLine(output, "event=product.update.name_in_use | ", tenants().tenantId(tenant));
		assertTrue(createLine.contains(" INFO "), createLine);
		assertTrue(updateLine.contains(" INFO "), updateLine);
		assertTrue(updateLine.contains("productId=" + id), updateLine);
		assertFalse(createLine.toLowerCase(Locale.ROOT).contains("checkout"), createLine);
		assertFalse(updateLine.toLowerCase(Locale.ROOT).contains("checkout"), updateLine);
	}

	/** Finds the refusal line of this tenant and checks that no stack trace follows it. */
	private static String logLine(CapturedOutput output, String event, String tenant) {
		List<String> lines = output.getAll().lines().toList();
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (line.contains(event) && line.contains("tenantId=" + tenant)) {
				String next = index + 1 < lines.size() ? lines.get(index + 1) : "";
				assertFalse(next.contains("Exception") || next.startsWith("\tat "), "stack trace after: " + line);
				return line;
			}
		}
		throw new AssertionError("log line not found: " + event + "tenantId=" + tenant);
	}
}
