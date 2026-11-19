package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lhamalog.core.dashboard.infrastructure.OpenObserveLogQueryAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenObserveLogQueryAdapterParseTest {

	@Test
	void parsesSearchHitsFromSeverityNumberAndText() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		var response = mapper.readTree("""
				{
				  "hits": [
				    {"hour_start":"2026-11-18T14:00:00Z","severitynumber":17,"severitytext":"Error","service_name":"checkout","cnt":4},
				    {"hour_start":"2026-11-18T14:00:00Z","severitynumber":13,"severitytext":"Warn","service_name":"checkout","cnt":2},
				    {"hour_start":"2026-11-18T14:00:00Z","severitynumber":0,"severitytext":null,"service_name":"checkout","cnt":3},
				    {"hour_start":"2026-11-18T14:00:00Z","severitynumber":9,"severitytext":"Info","service_name":null,"cnt":1}
				  ]
				}
				""");
		List<LogCountBucket> buckets = OpenObserveLogQueryAdapter.parseHits(response,
				EnumSet.allOf(LogSeverity.class));
		assertEquals(4, buckets.size());
		assertEquals(LogSeverity.ERROR, buckets.get(0).severity());
		assertEquals(LogSeverity.WARNING, buckets.get(1).severity());
		assertEquals(LogSeverity.INFO, buckets.get(2).severity());
		assertEquals(LogSeverity.INFO, buckets.get(3).severity());
		assertEquals("unknown", buckets.get(3).serviceName());
		assertEquals(Instant.parse("2026-11-18T14:00:00Z"), buckets.get(0).hourStart());
	}

	@Test
	void parsesRealOpenObserveHistogramWithoutOffsetAndMissingService() throws Exception {
		// Shape returned by the local OpenObserve v1.1.2 for the LLT-187/testenv streams (18/11/2026).
		ObjectMapper mapper = new ObjectMapper();
		var response = mapper.readTree("""
				{
				  "hits": [
				    {"hour_start":"2026-11-17T23:00:00","severitynumber":17,"severitytext":"ERROR","service_name":"testenv-checkout","cnt":1},
				    {"hour_start":"2026-11-18T00:00:00","severitynumber":9,"severitytext":"INFO","cnt":2}
				  ]
				}
				""");
		List<LogCountBucket> buckets = OpenObserveLogQueryAdapter.parseHits(response,
				EnumSet.allOf(LogSeverity.class));
		assertEquals(2, buckets.size());
		assertEquals(Instant.parse("2026-11-17T23:00:00Z"), buckets.get(0).hourStart());
		assertEquals(LogSeverity.ERROR, buckets.get(0).severity());
		assertEquals(Instant.parse("2026-11-18T00:00:00Z"), buckets.get(1).hourStart());
		assertEquals("unknown", buckets.get(1).serviceName());
	}

	@Test
	void buildsSqlWithLowercaseFieldsStreamAndEscapedLiterals() {
		OpenObserveProperties properties = defaultProperties();
		String sql = OpenObserveLogQueryAdapter.buildSql(properties, "llt187-dash-tenant",
				"llt187-dash-product");
		assertEquals(
				"SELECT histogram(_timestamp, '1 hour') AS hour_start, "
						+ "severitynumber AS severitynumber, severitytext AS severitytext, "
						+ "servicename AS service_name, count(*) AS cnt "
						+ "FROM \"logs_llt187_dash_tenant_94439d83\" "
						+ "WHERE tenantid = 'llt187-dash-tenant' AND productid = 'llt187-dash-product' "
						+ "GROUP BY hour_start, severitynumber, severitytext, service_name ORDER BY hour_start",
				sql);
	}

	@Test
	void escapesSingleQuotesInLiterals() {
		OpenObserveProperties properties = defaultProperties();
		String sql = OpenObserveLogQueryAdapter.buildSql(properties, "ten'ant", "prod'uct");
		assertTrue(sql.contains("tenantid = 'ten''ant'"));
		assertTrue(sql.contains("productid = 'prod''uct'"));
	}

	@Test
	void rejectsUnsafeFieldIdentifiers() {
		OpenObserveProperties properties = new OpenObserveProperties(
				"http://localhost:5080", "lhamalog", "Basic x", "logs",
				"tenantid; drop", "productid", "severitynumber", "severitytext", "servicename",
				Duration.ofSeconds(2), Duration.ofSeconds(10));
		assertThrows(IllegalArgumentException.class,
				() -> OpenObserveLogQueryAdapter.buildSql(properties, "acme", "p1"));
	}

	@Test
	void convertsInstantToOpenObserveMicros() {
		Instant from = Instant.parse("2026-11-16T00:00:00Z");
		Instant to = Instant.parse("2026-11-18T00:00:00Z");
		assertEquals(1_794_787_200_000_000L, OpenObserveLogQueryAdapter.toMicros(from));
		assertEquals(1_794_960_000_000_000L, OpenObserveLogQueryAdapter.toMicros(to));
	}

	/**
	 * Manual conference SQL for tenant/product LLT-187 sample window (hour×severity×service;
	 * ranking is derived from the same buckets in HybridMetricsMerger).
	 */
	@Test
	void printsConferenceSqlForLlt187Sample() {
		OpenObserveProperties properties = defaultProperties();
		String tenantId = "llt187-dash-tenant";
		String productId = "llt187-dash-product";
		Instant from = Instant.parse("2026-11-16T00:00:00Z");
		Instant to = Instant.parse("2026-11-18T00:00:00Z");
		String sql = OpenObserveLogQueryAdapter.buildSql(properties, tenantId, productId);
		System.out.println("=== OpenObserve conference SQL (count by hour × severity × service) ===");
		System.out.println(sql);
		System.out.println("start_time (µs)=" + OpenObserveLogQueryAdapter.toMicros(from));
		System.out.println("end_time (µs)=" + OpenObserveLogQueryAdapter.toMicros(to));
		System.out.println("stream=" + properties.streamForTenant(tenantId));
		System.out.println("=== Service ranking uses the same SQL; HybridMetricsMerger ranks by ERROR ===");
		assertTrue(sql.contains("logs_llt187_dash_tenant_94439d83"));
	}

	private static OpenObserveProperties defaultProperties() {
		return new OpenObserveProperties(
				"http://localhost:5080", "lhamalog", "Basic x", "logs",
				"tenantid", "productid", "severitynumber", "severitytext", "servicename",
				Duration.ofSeconds(2), Duration.ofSeconds(10));
	}
}
