package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.ObjectMapper;

/**
 * SC-004 of spec 012 (T035): with 10,000 occurrences in one company, the first page answers in under 1 s without
 * filters, with filters and with a search. Slow to prepare, so it runs only on demand:
 * {@code .\mvnw.cmd test -Dtest=OccurrencePerformanceTests -Doccurrence.performance=true}.
 */
@CoreIntegrationTest
@EnabledIfSystemProperty(named = "occurrence.performance", matches = "true")
class OccurrencePerformanceTests {

	private static final int OCCURRENCES = 10_000;

	private static final int RUNS = 5;

	private static final long LIMIT_MS = 1_000;

	private static final String[] TYPES = { "ERROR", "WARNING", "INFO" };

	private static final String[] STATUSES = { "ABERTA", "EM_ANALISE", "FECHADA" };

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	@Test
	void firstPageAnswersInUnderOneSecondWithTenThousandOccurrences() throws Exception {
		TestTenants tenants = new TestTenants(mongo);
		String organization = "org_test_" + UUID.randomUUID();
		String tenantId = tenants.tenantId(organization);
		RequestPostProcessor admin = tenants.session(organization, "admin_" + UUID.randomUUID(), "admin");
		List<String> products = new ArrayList<>();
		for (String name : List.of("api-gateway", "auth-service", "payment-service")) {
			products.add(mapper.readTree(mvc.perform(post("/api/products").with(admin)
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"context\":{}}"))
					.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString());
		}

		OccurrenceFixtures fixtures = new OccurrenceFixtures(mongo);
		Instant now = Instant.parse("2026-11-20T12:00:00Z");
		long seedStart = System.nanoTime();
		for (int i = 0; i < OCCURRENCES; i++) {
			Instant last = now.minus(Duration.ofMinutes(i * 7L));
			fixtures.occurrence(tenantId, products.get(i % products.size()))
				.type(TYPES[i % TYPES.length]).status(STATUSES[(i / 3) % STATUSES.length])
				.title(i % 50 == 0 ? "Timeout no banco " + i : "Falha " + i).message("message " + i)
				.count(1 + i % 40).seen(last.minus(Duration.ofHours(1)), last)
				.samples(List.of("ERROR [svc] linha de exemplo " + i)).insert();
		}
		long seedMs = (System.nanoTime() - seedStart) / 1_000_000;
		System.out.printf("event=occurrence.performance.seeded | occurrences=%d seedMs=%d%n", OCCURRENCES, seedMs);

		String from = now.minus(Duration.ofDays(30)).toString();
		String to = now.toString();
		measure(admin, "sem filtros", "");
		measure(admin, "com filtros", "?type=ERROR&status=ABERTA&productId=" + products.get(0) + "&from=" + from
				+ "&to=" + to);
		measure(admin, "com busca", "?q=timeout");
	}

	/** Runs the request RUNS times after one warm-up call and checks the slowest run against the limit. */
	private void measure(RequestPostProcessor admin, String label, String query) throws Exception {
		mvc.perform(get("/api/occurrences" + query).with(admin)).andExpect(status().isOk());
		List<Long> times = new ArrayList<>();
		long total = -1;
		for (int run = 0; run < RUNS; run++) {
			long start = System.nanoTime();
			String body = mvc.perform(get("/api/occurrences" + query).with(admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			times.add((System.nanoTime() - start) / 1_000_000);
			total = mapper.readTree(body).get("totalItems").asLong();
		}
		List<Long> sorted = times.stream().sorted().toList();
		System.out.printf("event=occurrence.performance.measured | case=%s totalItems=%d runsMs=%s medianMs=%d maxMs=%d%n",
				label, total, times, sorted.get(RUNS / 2), sorted.getLast());
		assertThat(total).isPositive();
		assertThat(sorted.getLast()).as("primeira página " + label).isLessThan(LIMIT_MS);
	}

}
