package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.occurrence.infrastructure.LocalOccurrenceSampleSeeder;
import br.com.lhamalog.core.occurrence.infrastructure.OccurrenceDocument;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 012 (LLT-135): occurrence list, filters and detail read by the Web. */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OccurrenceQueryIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	private TestTenants tenants;
	private OccurrenceFixtures fixtures;

	private TestTenants tenants() {
		if (tenants == null) tenants = new TestTenants(mongo);
		return tenants;
	}

	private OccurrenceFixtures fixtures() {
		if (fixtures == null) fixtures = new OccurrenceFixtures(mongo);
		return fixtures;
	}

	/** One company per test: organization, tenant and an admin session. */
	private final class Company {
		final String organization = "org_test_" + UUID.randomUUID();
		final String tenantId = tenants().tenantId(organization);
		final RequestPostProcessor admin = tenants().session(organization, "admin_" + UUID.randomUUID(), "admin");

		RequestPostProcessor member(String user) {
			return tenants().session(organization, user, "member");
		}

		/** Product created through the API, so the creator gets access to it (feature 001). */
		String product(RequestPostProcessor actor, String name) throws Exception {
			return json(mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"" + name + "\",\"context\":{}}"))
					.andExpect(status().isCreated())).get("id").asString();
		}

		OccurrenceFixtures.Builder occurrence(String productId) {
			return fixtures().occurrence(tenantId, productId);
		}
	}

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode list(RequestPostProcessor actor, String query) throws Exception {
		return json(mvc.perform(get("/api/occurrences" + query).with(actor)).andExpect(status().isOk()));
	}

	private static List<String> ids(JsonNode page) {
		List<String> ids = new ArrayList<>();
		page.get("items").forEach(item -> ids.add(item.get("id").asString()));
		return ids;
	}

	private static String iso(Instant instant) {
		return instant.toString();
	}

	private static Instant hoursAgo(long hours) {
		return NOW.minus(Duration.ofHours(hours));
	}

	private static Instant daysAgo(long days) {
		return NOW.minus(Duration.ofDays(days));
	}

	// US1 — listar com paginação

	@Test
	void adminSeesEveryProductIncludingArchivedNewestFirst() throws Exception {
		Company company = new Company();
		String active = company.product(company.admin, "Produto ativo");
		String archived = company.product(company.admin, "Produto arquivado");
		mongo.updateFirst(Query.query(Criteria.where("_id").is(archived)), Update.update("archivedAt", NOW),
				Product.class);
		String newest = company.occurrence(active).lastSeen(hoursAgo(1)).insert();
		String middle = company.occurrence(archived).lastSeen(hoursAgo(2)).insert();
		String oldest = company.occurrence(active).lastSeen(hoursAgo(3)).insert();
		Company other = new Company();
		other.occurrence(other.product(other.admin, "Outro")).lastSeen(hoursAgo(0)).insert();

		JsonNode page = list(company.admin, "");

		assertThat(ids(page)).containsExactly(newest, middle, oldest);
		assertThat(page.get("totalItems").asLong()).isEqualTo(3);
		assertThat(page.get("page").asInt()).isEqualTo(1);
		assertThat(page.get("size").asInt()).isEqualTo(20);
		JsonNode first = page.get("items").get(0);
		assertThat(first.get("product").get("id").asString()).isEqualTo(active);
		assertThat(first.get("product").get("name").asString()).isEqualTo("Produto ativo");
		assertThat(first.get("type").asString()).isEqualTo("ERROR");
		assertThat(first.get("status").asString()).isEqualTo("ABERTA");
		assertThat(first.get("count").asLong()).isEqualTo(1);
		assertThat(first.has("recurring")).isTrue();
		assertThat(first.get("lastSeenAt").asString()).isEqualTo(iso(hoursAgo(1)));
	}

	@Test
	void sameLastSeenIsOrderedByIdDescending() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		company.occurrence(product).id("a-" + UUID.randomUUID()).lastSeen(hoursAgo(1)).insert();
		String second = company.occurrence(product).id("b-" + UUID.randomUUID()).lastSeen(hoursAgo(1)).insert();

		assertThat(ids(list(company.admin, "")).getFirst()).isEqualTo(second);
	}

	@Test
	void memberOnlySeesProductsWithAccess() throws Exception {
		Company company = new Company();
		String adminProduct = company.product(company.admin, "Do admin");
		RequestPostProcessor member = company.member("member_" + UUID.randomUUID());
		String memberProduct = company.product(member, "Do membro");
		company.occurrence(adminProduct).insert();
		String visible = company.occurrence(memberProduct).insert();

		JsonNode page = list(member, "");

		assertThat(ids(page)).containsExactly(visible);
		assertThat(page.get("totalItems").asLong()).isEqualTo(1);
	}

	@Test
	void pagesCoverEveryOccurrenceExactlyOnce() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		IntStream.range(0, 25).forEach(i -> company.occurrence(product).lastSeen(hoursAgo(i % 4)).insert());

		Set<String> seen = new HashSet<>();
		List<String> all = new ArrayList<>();
		for (int page = 1; page <= 3; page++) {
			JsonNode result = list(company.admin, "?size=10&page=" + page);
			assertThat(result.get("totalItems").asLong()).isEqualTo(25);
			all.addAll(ids(result));
			seen.addAll(ids(result));
		}
		assertThat(all).hasSize(25);
		assertThat(seen).hasSize(25);

		JsonNode beyond = list(company.admin, "?size=10&page=4");
		assertThat(beyond.get("items")).isEmpty();
		assertThat(beyond.get("totalItems").asLong()).isEqualTo(25);
	}

	@Test
	void emptyCompanyAndMemberWithoutProductsGetAnEmptyPage() throws Exception {
		Company company = new Company();
		JsonNode empty = list(company.admin, "");
		assertThat(empty.get("items")).isEmpty();
		assertThat(empty.get("totalItems").asLong()).isZero();

		company.occurrence(company.product(company.admin, "Só do admin")).insert();
		JsonNode member = list(company.member("member_" + UUID.randomUUID()), "");
		assertThat(member.get("items")).isEmpty();
		assertThat(member.get("totalItems").asLong()).isZero();
	}

	@Test
	void invalidPageParametersAnswer400WithTheField() throws Exception {
		Company company = new Company();
		JsonNode error = json(mvc.perform(get("/api/occurrences?size=15&page=0").with(company.admin))
				.andExpect(status().isBadRequest()));
		assertThat(error.get("fieldErrors").get("size").asString()).isEqualTo("Use 10, 20 ou 50.");
		assertThat(error.get("fieldErrors").get("page").asString()).isEqualTo("A página começa em 1.");
	}

	@Test
	void invalidDocumentsStayOutOfTheListAndTheTotal(CapturedOutput output) throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String valid = company.occurrence(product).insert();
		String unknownType = "invalid-type-" + UUID.randomUUID();
		fixtures().insertRaw(new Document("_id", unknownType).append("tenantId", company.tenantId)
			.append("productId", product).append("signature", "CRITICAL|x|svc|host").append("type", "CRITICAL")
			.append("status", "ABERTA").append("count", 1L).append("firstSeenAt", java.util.Date.from(NOW))
			.append("lastSeenAt", java.util.Date.from(NOW)));
		String noStatus = "invalid-status-" + UUID.randomUUID();
		fixtures().insertRaw(new Document("_id", noStatus).append("tenantId", company.tenantId)
			.append("productId", product).append("signature", "ERROR|y|svc|host").append("type", "ERROR")
			.append("title", "Texto secreto do título").append("count", 1L)
			.append("firstSeenAt", java.util.Date.from(NOW)).append("lastSeenAt", java.util.Date.from(NOW)));

		JsonNode page = list(company.admin, "");
		assertThat(ids(page)).containsExactly(valid);
		assertThat(page.get("totalItems").asLong()).isEqualTo(1);

		JsonNode error = json(mvc.perform(get("/api/occurrences/" + noStatus).with(company.admin))
				.andExpect(status().isNotFound()));
		assertThat(error.get("message").asString()).isEqualTo("Ocorrência não encontrada ou sem acesso.");
		assertThat(output.getOut()).contains("event=occurrence.read.invalid")
			.contains("occurrenceId=" + noStatus)
			.contains("reason=unknown_status")
			.doesNotContain("Texto secreto do título");
	}

	@Test
	void occurrenceWithoutTitleShowsTheMessageFromTheSignature() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String id = company.occurrence(product).title(null).message("connection timeout | retry").insert();

		JsonNode item = list(company.admin, "").get("items").get(0);
		assertThat(item.get("id").asString()).isEqualTo(id);
		assertThat(item.get("title").asString()).isEqualTo("connection timeout | retry");
	}

	// US2 — filtrar e buscar

	@Test
	void typesAndStatusesCombine() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String errorOpen = company.occurrence(product).type("ERROR").status("ABERTA").insert();
		String warningAnalysis = company.occurrence(product).type("WARNING").status("EM_ANALISE").insert();
		company.occurrence(product).type("ERROR").status("FECHADA").insert();
		company.occurrence(product).type("INFO").status("ABERTA").insert();

		JsonNode page = list(company.admin, "?type=ERROR&type=WARNING&status=ABERTA&status=EM_ANALISE");

		assertThat(ids(page)).containsExactlyInAnyOrder(errorOpen, warningAnalysis);
		assertThat(page.get("totalItems").asLong()).isEqualTo(2);
	}

	@Test
	void periodSelectsWhatHappenedInsideIt() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String inside = company.occurrence(product).seen(daysAgo(6), daysAgo(1)).insert();
		String endedInside = company.occurrence(product).seen(daysAgo(9), daysAgo(5)).insert();
		company.occurrence(product).seen(daysAgo(10), daysAgo(8)).insert();
		String old = company.occurrence(product).seen(daysAgo(20), daysAgo(15)).insert();

		assertThat(ids(list(company.admin, "?from=" + iso(daysAgo(7)) + "&to=" + iso(daysAgo(2)))))
			.containsExactlyInAnyOrder(inside, endedInside);
		assertThat(ids(list(company.admin, "?from=" + iso(daysAgo(7))))).containsExactlyInAnyOrder(inside, endedInside);
		assertThat(ids(list(company.admin, "?to=" + iso(daysAgo(12))))).containsExactly(old);
	}

	@Test
	void productFilterAcceptsVisibleProductsAndRefusesOthers() throws Exception {
		Company company = new Company();
		String first = company.product(company.admin, "Primeiro");
		String second = company.product(company.admin, "Segundo");
		String third = company.product(company.admin, "Terceiro");
		String a = company.occurrence(first).insert();
		String b = company.occurrence(second).insert();
		company.occurrence(third).insert();

		assertThat(ids(list(company.admin, "?productId=" + first + "&productId=" + second)))
			.containsExactlyInAnyOrder(a, b);

		Company other = new Company();
		String foreign = other.product(other.admin, "De outra empresa");
		JsonNode error = json(mvc.perform(get("/api/occurrences?productId=" + foreign).with(company.admin))
				.andExpect(status().isNotFound()));
		assertThat(error.get("message").asString()).isEqualTo("Produto não encontrado ou sem acesso.");

		RequestPostProcessor member = company.member("member_" + UUID.randomUUID());
		mvc.perform(get("/api/occurrences?productId=" + first).with(member)).andExpect(status().isNotFound());
	}

	@Test
	void searchLooksAtTitleSummarySignatureAndExactId() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String byTitle = company.occurrence(product).title("Erro de TIMEOUT no banco").insert();
		String bySummary = company.occurrence(product).title("Outro").summary("Houve um timeout ontem").insert();
		String bySignature = company.occurrence(product).title(null).message("request timeout reached").insert();
		String byId = company.occurrence(product).title("Sem relação").insert();
		company.occurrence(product).title("Nada a ver").insert();

		assertThat(ids(list(company.admin, "?q=timeout")))
			.containsExactlyInAnyOrder(byTitle, bySummary, bySignature);
		assertThat(ids(list(company.admin, "?q=" + byId))).containsExactly(byId);
		assertThat(list(company.admin, "?q=(").get("totalItems").asLong()).isZero();
		assertThat(list(company.admin, "?q=.*").get("totalItems").asLong()).isZero();
	}

	@Test
	void filtersStayOnTheNextPageAndEmptyResultsAreNotErrors() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		IntStream.range(0, 12).forEach(i -> company.occurrence(product).type("WARNING").lastSeen(hoursAgo(i)).insert());
		IntStream.range(0, 5).forEach(i -> company.occurrence(product).type("ERROR").insert());

		JsonNode second = list(company.admin, "?type=WARNING&size=10&page=2");
		assertThat(second.get("totalItems").asLong()).isEqualTo(12);
		assertThat(second.get("items")).hasSize(2);
		second.get("items").forEach(item -> assertThat(item.get("type").asString()).isEqualTo("WARNING"));

		JsonNode none = list(company.admin, "?type=INFO");
		assertThat(none.get("items")).isEmpty();
		assertThat(none.get("totalItems").asLong()).isZero();

		assertThat(list(company.admin, "").get("totalItems").asLong()).isEqualTo(17);
	}

	// US3 — detalhe

	@Test
	void detailHasEveryPartOfTheContract() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "api-gateway");
		List<String> lines = new ArrayList<>();
		IntStream.range(1, 7).forEach(i -> lines.add("linha " + i + " <EMAIL>"));
		lines.add("x".repeat(600));
		String id = company.occurrence(product).type("WARNING").title("Falha").summary("Resumo da falha")
			.message("fila com atraso").service("coletor").hostName("host-sintetico").traceId("trace-1")
			.correlationId("corr-1").moduleId("mod-1").lineNumber(42).extracted("http.route", "/api/x")
			.sensitiveData(true).recurring(true).count(13).seen(daysAgo(2), hoursAgo(1)).samples(lines)
			.change("ABERTA", "EM_ANALISE", daysAgo(1), "USER", "user_1")
			.change(null, "ABERTA", daysAgo(2), "SYSTEM", "modulo-ocorrencia")
			.status("EM_ANALISE")
			.insert();

		JsonNode detail = json(mvc.perform(get("/api/occurrences/" + id).with(company.admin))
				.andExpect(status().isOk()));

		assertThat(detail.get("id").asString()).isEqualTo(id);
		assertThat(detail.get("type").asString()).isEqualTo("WARNING");
		assertThat(detail.get("title").asString()).isEqualTo("Falha");
		assertThat(detail.get("summary").asString()).isEqualTo("Resumo da falha");
		assertThat(detail.get("product").get("name").asString()).isEqualTo("api-gateway");
		assertThat(detail.get("count").asLong()).isEqualTo(13);
		assertThat(detail.get("status").asString()).isEqualTo("EM_ANALISE");
		assertThat(detail.get("recurring").asBoolean()).isTrue();
		assertThat(detail.get("sensitiveData").asBoolean()).isTrue();
		assertThat(detail.get("firstSeenAt").asString()).isEqualTo(iso(daysAgo(2)));

		JsonNode samples = detail.get("samples");
		assertThat(samples).hasSize(5);
		assertThat(samples.get(0).asString()).hasSize(512);
		assertThat(samples.get(1).asString()).isEqualTo("linha 6 <EMAIL>");
		assertThat(samples.get(4).asString()).isEqualTo("linha 3 <EMAIL>");

		List<String> attributes = new ArrayList<>();
		detail.get("attributes").forEach(a -> attributes.add(a.get("name").asString() + "=" + a.get("value").asString()));
		assertThat(attributes).containsExactly("correlation_id=corr-1", "host.name=host-sintetico",
				"http.route=/api/x", "line_number=42", "module_id=mod-1", "service.name=coletor", "trace_id=trace-1");

		JsonNode history = detail.get("statusHistory");
		assertThat(history).hasSize(2);
		assertThat(history.get(0).get("from").isNull()).isTrue();
		assertThat(history.get(0).get("to").asString()).isEqualTo("ABERTA");
		assertThat(history.get(0).get("changedBy").get("type").asString()).isEqualTo("SYSTEM");
		assertThat(history.get(1).get("to").asString()).isEqualTo("EM_ANALISE");
		assertThat(history.get(1).get("changedBy").get("id").asString()).isEqualTo("user_1");
	}

	@Test
	void detailWithoutOptionalPartsComesEmpty() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String id = company.occurrence(product).title(null).summary(null).message("só a assinatura")
			.service("svc-x").hostName("host-x").insert();

		JsonNode detail = json(mvc.perform(get("/api/occurrences/" + id).with(company.admin))
				.andExpect(status().isOk()));

		assertThat(detail.get("title").asString()).isEqualTo("só a assinatura");
		assertThat(detail.get("summary").isNull()).isTrue();
		assertThat(detail.get("sensitiveData").isNull()).isTrue();
		assertThat(detail.get("samples")).isEmpty();
		assertThat(detail.get("statusHistory")).isEmpty();
		List<String> names = new ArrayList<>();
		detail.get("attributes").forEach(a -> names.add(a.get("name").asString()));
		assertThat(names).containsExactly("host.name", "service.name");
	}

	@Test
	void detailAnswersTheSame404ForOtherCompanyNoAccessAndMissing() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin, "Produto");
		String id = company.occurrence(product).insert();
		Company other = new Company();
		RequestPostProcessor member = company.member("member_" + UUID.randomUUID());

		for (MockHttpServletRequestBuilder request : List.of(get("/api/occurrences/" + id).with(other.admin),
				get("/api/occurrences/" + id).with(member),
				get("/api/occurrences/" + UUID.randomUUID()).with(company.admin))) {
			JsonNode error = json(mvc.perform(request).andExpect(status().isNotFound()));
			assertThat(error.get("message").asString()).isEqualTo("Ocorrência não encontrada ou sem acesso.");
		}
	}

	// US4 — carga de exemplo local

	@Test
	void sampleDataCoversTheCasesAndNeverDuplicates() throws Exception {
		Company company = new Company();
		String first = company.product(company.admin, "Amostra 1");
		String second = company.product(company.admin, "Amostra 2");
		LocalOccurrenceSampleSeeder seeder = new LocalOccurrenceSampleSeeder(mongo);

		seeder.seed();
		seeder.seed();

		for (String product : List.of(first, second)) {
			List<OccurrenceDocument> samples = mongo.find(Query.query(Criteria.where("productId").is(product)),
					OccurrenceDocument.class);
			assertThat(samples).hasSize(12);
			assertThat(samples).extracting(OccurrenceDocument::type).contains("ERROR", "WARNING", "INFO");
			assertThat(samples).extracting(OccurrenceDocument::status).contains("ABERTA", "EM_ANALISE", "FECHADA");
			assertThat(samples).extracting(OccurrenceDocument::recurring).contains(true, false);
			assertThat(samples).anyMatch(sample -> sample.title() == null);
			assertThat(samples).anyMatch(sample -> sample.samples().stream().anyMatch(line -> line.contains("<EMAIL>")));
			assertThat(samples).allMatch(sample -> sample.tenantId().equals(company.tenantId));
		}
		JsonNode page = list(company.admin, "?size=50");
		assertThat(page.get("totalItems").asLong()).isEqualTo(24);
		JsonNode detail = json(mvc.perform(get("/api/occurrences/" + ids(page).getFirst()).with(company.admin))
				.andExpect(status().isOk()));
		assertThat(detail.get("statusHistory")).isNotEmpty();
	}

}
