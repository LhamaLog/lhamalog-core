package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import tools.jackson.databind.ObjectMapper;

/**
 * SC-004 of spec 013 (T033): a lookup under 200 ms and a delivery of 100 findings under 2 s. Runs only on demand:
 * {@code .\mvnw.cmd test -Dtest=OccurrenceModulePerformanceTests -Doccurrence.performance=true}.
 */
@CoreIntegrationTest
@EnabledIfSystemProperty(named = "occurrence.performance", matches = "true")
class OccurrenceModulePerformanceTests {

	private static final int FINDINGS = 100;

	private static final int RUNS = 5;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	@Test
	void lookupAndDeliveryStayWithinTheClientTimeouts() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		// Warm-up with one delivery and one lookup.
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("aquecimento"))).build())
			.andExpect(status().isCreated());

		List<Long> creations = new ArrayList<>();
		List<Long> updates = new ArrayList<>();
		List<Long> lookups = new ArrayList<>();
		for (int run = 0; run < RUNS; run++) {
			List<String> signatures = new ArrayList<>();
			InternalModuleWorld.Summary created = InternalModuleWorld.summary(company);
			for (int i = 0; i < FINDINGS; i++) {
				String signature = InternalModuleWorld.signature("rodada " + run + " achado " + i);
				signatures.add(signature);
				created.finding(InternalModuleWorld.finding(signature)
					.set("camposExtraido", Map.of("http.route", "/api/" + i)));
			}
			creations.add(time(() -> world.call(InternalModuleWorld.DELIVERY, created.build()).andExpect(status().isCreated())));

			InternalModuleWorld.Summary updated = InternalModuleWorld.summary(company);
			for (String signature : signatures) {
				long start = System.nanoTime();
				String revision = world.json(world.call(InternalModuleWorld.LOOKUP,
						InternalModuleWorld.lookup(company, signature, "ERROR")).andExpect(status().isOk()))
					.get("estado").get("revisao").asString();
				lookups.add((System.nanoTime() - start) / 1_000_000);
				updated.finding(InternalModuleWorld.finding(signature).count(6).revision(revision));
			}
			updates.add(time(() -> world.call(InternalModuleWorld.DELIVERY, updated.build()).andExpect(status().isCreated())));
		}
		List<Long> sortedLookups = lookups.stream().sorted().toList();
		long lookupP95 = sortedLookups.get((int) Math.ceil(sortedLookups.size() * 0.95) - 1);
		System.out.printf(
				"event=occurrence.performance.measured | findings=%d creationMs=%s updateMs=%s lookupMedianMs=%d lookupP95Ms=%d lookupMaxMs=%d%n",
				FINDINGS, creations, updates, sortedLookups.get(sortedLookups.size() / 2), lookupP95, sortedLookups.getLast());
		assertThat(creations).allMatch(ms -> ms < 2_000);
		assertThat(updates).allMatch(ms -> ms < 2_000);
		assertThat(lookupP95).isLessThan(200);
	}

	private static long time(ThrowingRunnable call) throws Exception {
		long start = System.nanoTime();
		call.run();
		return (System.nanoTime() - start) / 1_000_000;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {

		void run() throws Exception;

	}

}
