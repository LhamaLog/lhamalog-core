package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.mongodb.client.model.Filters;

import br.com.lhamalog.core.occurrence.InternalModuleWorld.Company;
import br.com.lhamalog.core.occurrence.InternalModuleWorld.Summary;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Spec 013, US2 and US3 on the real database: {@code POST /internal/v1/resumos-analise}. */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AnalysisSummaryDeliveryIntegrationTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	private InternalModuleWorld world() {
		return new InternalModuleWorld(mvc, mapper, mongo);
	}

	private List<Document> occurrencesOf(Company company) {
		return mongo.getCollection("Ocorrencia")
			.find(Filters.and(Filters.eq("tenantId", company.tenantId()), Filters.eq("productId", company.productId())))
			.into(new ArrayList<>());
	}

	private Document occurrence(Company company, String signature) {
		return mongo.getCollection("Ocorrencia")
			.find(Filters.and(Filters.eq("tenantId", company.tenantId()), Filters.eq("signature", signature)))
			.first();
	}

	private String revision(InternalModuleWorld world, Company company, String signature) throws Exception {
		JsonNode state = world.json(world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, signature, "ERROR"))
			.andExpect(status().isOk())).get("estado");
		return state.isNull() ? null : state.get("revisao").asString();
	}

	// US2

	@Test
	void firstDeliveryWritesTheOccurrencesTheScreenReads(CapturedOutput output) throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String signature = InternalModuleWorld.signature("timeout ao gravar no banco");
		Map<String, Object> body = InternalModuleWorld.summary(company)
			.exec("V0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11")
			.finding(InternalModuleWorld.finding(signature)
				.set("titulo", "Timeout no banco")
				.set("resumo", "Resumo mascarado")
				.set("numeroLinha", 42)
				.set("camposExtraido", Map.of("http.route", "/api/pay"))
				.set("dadosSensiveis", true)
				.set("traceId", "trace-1")
				.set("recorrente", true))
			.finding(InternalModuleWorld.finding("WARNING|fila lenta|svc|host-1").type("WARNING").count(1))
			.build();
		JsonNode receipt = world.json(world.call(InternalModuleWorld.DELIVERY, body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.aceito").value(true))
			.andExpect(jsonPath("$.tenantId").value(company.tenantId()))
			.andExpect(jsonPath("$.productId").value(company.productId()))
			.andExpect(jsonPath("$.batchId").value(body.get("batchId")))
			.andExpect(jsonPath("$.execId").value("V0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11"))
			.andExpect(jsonPath("$.repeticao").value(false)));
		assertThat(receipt.get("reciboId").asString()).isNotBlank();
		assertThat(occurrencesOf(company)).hasSize(2);

		// The LLT-135 screen reads what the Module wrote, extracted field with a dot included.
		JsonNode page = world.json(mvc.perform(get("/api/occurrences").with(company.admin())).andExpect(status().isOk()));
		assertThat(page.get("totalItems").asLong()).isEqualTo(2);
		String id = occurrence(company, signature).getString("_id");
		JsonNode detail = world.json(mvc.perform(get("/api/occurrences/" + id).with(company.admin())).andExpect(status().isOk()));
		assertThat(detail.get("title").asString()).isEqualTo("Timeout no banco");
		assertThat(detail.get("status").asString()).isEqualTo("ABERTA");
		assertThat(detail.get("count").asLong()).isEqualTo(3);
		assertThat(detail.get("recurring").asBoolean()).isTrue();
		assertThat(detail.get("samples").get(0).asString()).isEqualTo("linha 2");
		assertThat(detail.get("attributes").toString()).contains("http.route").contains("/api/pay").contains("line_number");
		assertThat(detail.get("statusHistory").get(0).get("changedBy").get("type").asString()).isEqualTo("SYSTEM");

		Document stored = mongo.getCollection("ReciboResumoAnalise")
			.find(Filters.eq("reciboId", receipt.get("reciboId").asString()))
			.first();
		assertThat(stored.getString("state")).isEqualTo("CONFIRMED");
		assertThat(stored.containsKey("journal")).isFalse();
		assertThat(stored.getDate("expiresAt").toInstant())
			.isBetween(Instant.now().plus(Duration.ofDays(29)), Instant.now().plus(Duration.ofDays(31)));

		assertThat(output).contains("event=occurrence.delivery.accepted")
			.doesNotContain("timeout ao gravar no banco")
			.doesNotContain("linha 1 <EMAIL>")
			.doesNotContain("/api/pay");
	}

	@Test
	void repeatedKeyReturnsTheSameReceiptAndWritesNothing() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String signature = InternalModuleWorld.signature("repetida");
		Summary first = InternalModuleWorld.summary(company).finding(InternalModuleWorld.finding(signature));
		Map<String, Object> body = first.build();
		String receiptId = world.json(world.call(InternalModuleWorld.DELIVERY, body).andExpect(status().isCreated()))
			.get("reciboId").asString();
		List<Document> before = occurrencesOf(company);

		world.call(InternalModuleWorld.DELIVERY, body)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reciboId").value(receiptId))
			.andExpect(jsonPath("$.repeticao").value(true));
		// Recalculated after a restart: other numbers and a revision that is no longer current, same key.
		Map<String, Object> recalculated = InternalModuleWorld.summary(company)
			.batch((String) body.get("batchId"))
			.finding(InternalModuleWorld.finding(signature).count(99).revision("revisao-velha"))
			.build();
		world.call(InternalModuleWorld.DELIVERY, recalculated)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reciboId").value(receiptId));
		assertThat(occurrencesOf(company)).isEqualTo(before);
	}

	@Test
	void anotherExecIdIsANewKey() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String signature = InternalModuleWorld.signature("varredura");
		String batch = "batch-" + UUID.randomUUID();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company).batch(batch)
				.exec("V0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11").finding(InternalModuleWorld.finding(signature)).build())
			.andExpect(status().isCreated());
		String revision = revision(world, company, signature);
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company).batch(batch)
				.exec("M0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c12")
				.finding(InternalModuleWorld.finding(signature).count(5).revision(revision)).build())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.repeticao").value(false));
		assertThat(occurrence(company, signature).get("count")).isEqualTo(5L);
	}

	@Test
	void summaryWithoutFindingsIsAccepted() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company).build())
			.andExpect(status().isCreated());
		assertThat(occurrencesOf(company)).isEmpty();
	}

	@Test
	void findingOfAClosedOccurrenceReopensIt() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String signature = InternalModuleWorld.signature("reaberta");
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature)).build())
			.andExpect(status().isCreated());
		mongo.getCollection("Ocorrencia").updateOne(Filters.eq("signature", signature),
				new Document("$set", new Document("status", "FECHADA")));
		String revision = revision(world, company, signature);
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature).count(4).revision(revision)
					.seen(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-21T10:00:00Z")))
				.build())
			.andExpect(status().isCreated());
		Document reopened = occurrence(company, signature);
		assertThat(reopened.getString("status")).isEqualTo("ABERTA");
		List<Document> history = reopened.getList("statusHistory", Document.class);
		assertThat(history).hasSize(2);
		assertThat(history.getLast().getString("from")).isEqualTo("FECHADA");
		assertThat(reopened.getList("samples", String.class)).hasSize(4);
		assertThat(reopened.getDate("lastSeenAt")).isEqualTo(Date.from(Instant.parse("2026-11-21T10:00:00Z")));
	}

	@Test
	void invalidSummaryWritesNothing() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("ok")))
				.finding(InternalModuleWorld.finding("FATAL|x|svc|h").type("FATAL"))
				.build())
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
		world.call(InternalModuleWorld.DELIVERY, "{\"tenantId\":")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
		assertThat(occurrencesOf(company)).isEmpty();
		assertThat(mongo.getCollection("ReciboResumoAnalise").countDocuments(Filters.eq("tenantId", company.tenantId())))
			.isZero();
	}

	// US3

	@Test
	void staleRevisionInOneFindingRefusesTheWholeSummary() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String a = InternalModuleWorld.signature("a"), b = InternalModuleWorld.signature("b");
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(a)).finding(InternalModuleWorld.finding(b)).build())
			.andExpect(status().isCreated());
		List<Document> before = occurrencesOf(company);
		Map<String, Object> stale = InternalModuleWorld.summary(company)
			.finding(InternalModuleWorld.finding(a).count(6).revision(revision(world, company, a)))
			.finding(InternalModuleWorld.finding(b).count(6).revision("revisao-de-ontem"))
			.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("c")))
			.build();
		world.call(InternalModuleWorld.DELIVERY, stale)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.codigo").value("REVISAO_DESATUALIZADA"));
		assertThat(occurrencesOf(company)).isEqualTo(before);

		// Same key again after a new lookup: there is no receipt yet, so it is a first delivery.
		Map<String, Object> retried = InternalModuleWorld.summary(company)
			.batch((String) stale.get("batchId"))
			.finding(InternalModuleWorld.finding(a).count(6).revision(revision(world, company, a)))
			.finding(InternalModuleWorld.finding(b).count(6).revision(revision(world, company, b)))
			.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("c")))
			.build();
		world.call(InternalModuleWorld.DELIVERY, retried).andExpect(status().isCreated());
		assertThat(occurrencesOf(company)).hasSize(3);
	}

	@Test
	void nullRevisionForAnExistingOccurrenceIsStale() throws Exception {
		InternalModuleWorld world = world();
		Company company = world.company();
		String a = InternalModuleWorld.signature("existe");
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(a)).build())
			.andExpect(status().isCreated());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(a)).build())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.codigo").value("REVISAO_DESATUALIZADA"));
	}

	@Test
	void parallelDeliveriesOfTheSameKeyHaveASingleEffect() throws Exception {
		for (int round = 0; round < 5; round++) {
			InternalModuleWorld world = world();
			Company company = world.company();
			List<Callable<Integer>> calls = new ArrayList<>();
			Map<String, Object> body = InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("paralela")))
				.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("paralela-2")))
				.build();
			for (int i = 0; i < 4; i++) {
				calls.add(() -> world.call(InternalModuleWorld.DELIVERY, body).andReturn().getResponse().getStatus());
			}
			List<Integer> statuses = runTogether(calls);
			assertThat(statuses).containsOnlyOnce(201);
			assertThat(statuses).allMatch(code -> code == 201 || code == 200 || code == 409);
			assertThat(occurrencesOf(company)).hasSize(2);
		}
	}

	@Test
	void twoBatchesThatSawTheSameRevisionKeepBothIncrements() throws Exception {
		for (int round = 0; round < 5; round++) {
			InternalModuleWorld world = world();
			Company company = world.company();
			String signature = InternalModuleWorld.signature("contagem");
			world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
					.finding(InternalModuleWorld.finding(signature).count(10)).build())
				.andExpect(status().isCreated());
			String seen = revision(world, company, signature);
			// Both lots consulted count 10: one brings 3 new logs (13), the other 5 (15).
			Map<String, Object> first = InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature).count(13).revision(seen)).build();
			Map<String, Object> second = InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature).count(15).revision(seen)).build();
			List<Integer> statuses = runTogether(List.of(
					() -> world.call(InternalModuleWorld.DELIVERY, first).andReturn().getResponse().getStatus(),
					() -> world.call(InternalModuleWorld.DELIVERY, second).andReturn().getResponse().getStatus()));
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
			long winner = ((Number) occurrence(company, signature).get("count")).longValue();
			Map<String, Object> loser = winner == 13 ? second : first;
			long increment = winner == 13 ? 5 : 3;
			// The loser consults again and recomputes under the same key.
			Map<String, Object> recomputed = InternalModuleWorld.summary(company)
				.batch((String) loser.get("batchId"))
				.finding(InternalModuleWorld.finding(signature).count(winner + increment)
					.revision(revision(world, company, signature)))
				.build();
			world.call(InternalModuleWorld.DELIVERY, recomputed).andExpect(status().isCreated());
			assertThat(occurrence(company, signature).get("count")).isEqualTo(18L);
		}
	}

	private static List<Integer> runTogether(List<Callable<Integer>> calls) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> call : calls) {
				futures.add(pool.submit(() -> {
					start.await();
					return call.call();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) statuses.add(future.get());
			return statuses;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
