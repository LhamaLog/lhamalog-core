package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lhamalog.core.dashboard.infrastructure.OpenObserveLogQueryAdapter;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenObserveLogQueryAdapterParseTest {

	@Test
	void parsesSearchHitsAndNormalizesSeverity() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		var response = mapper.readTree("""
				{
				  "hits": [
				    {"hour_start":"2026-11-18T14:00:00Z","severity":"error","service_name":"checkout","cnt":4},
				    {"hour_start":"2026-11-18T14:00:00Z","severity":"warn","service_name":"checkout","cnt":2},
				    {"hour_start":"2026-11-18T14:00:00Z","severity":"weird","service_name":"checkout","cnt":9}
				  ]
				}
				""");
		List<LogCountBucket> buckets = OpenObserveLogQueryAdapter.parseHits(response,
				EnumSet.allOf(LogSeverity.class));
		assertEquals(2, buckets.size());
		assertEquals(LogSeverity.ERROR, buckets.get(0).severity());
		assertEquals(LogSeverity.WARNING, buckets.get(1).severity());
		assertEquals(Instant.parse("2026-11-18T14:00:00Z"), buckets.get(0).hourStart());
	}
}
