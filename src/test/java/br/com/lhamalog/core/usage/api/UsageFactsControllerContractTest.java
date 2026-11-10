package br.com.lhamalog.core.usage.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lhamalog.core.usage.application.UsageFactService;
import br.com.lhamalog.core.usage.application.UsageCycleSummary;
import br.com.lhamalog.core.config.UsageFactsSecurityConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = UsageFactsController.class, properties = {
		"LHAMALOG_CORE_USAGE_TOKEN=test-token",
		"management.otlp.metrics.export.enabled=false",
		"management.tracing.export.otlp.enabled=false" })
@Import(UsageFactsSecurityConfiguration.class)
class UsageFactsControllerContractTest {

	private static final String TOKEN = "test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UsageFactService usageFacts;

	@Test
	void acceptsAValidFactOnlyWithTheDedicatedServiceToken() throws Exception {
		var acceptedAt = OffsetDateTime.parse("2026-11-09T12:00:00Z");
		var fact = new UsageFactRequest("fact-1", "batch-1", "tenant-1", "product-1",
				UsageDomain.LOGS, List.of("record-1"), 1, 42, acceptedAt, 1);
		org.mockito.Mockito.when(usageFacts.record(fact)).thenReturn(UsageFactService.RecordResult.CREATED);

		mockMvc.perform(post("/internal/usage-facts")
				.header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"usageFactId":"fact-1","batchId":"batch-1","tenantId":"tenant-1","productId":"product-1",
						 "domain":"LOGS","acceptedRecordIds":["record-1"],"acceptedRecordCount":1,
						 "acceptedBytes":42,"acceptedAt":"2026-11-09T12:00:00Z","schemaVersion":1}
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/internal/usage-facts").header("Authorization", "Bearer clerk-token")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void validatesContractAndExposesWindowAndCurrentCycleQueries() throws Exception {
		mockMvc.perform(post("/internal/usage-facts").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"usageFactId":"fact-1","batchId":"batch-1","tenantId":"tenant-1","productId":"product-1",
						 "domain":"METRICS","acceptedRecordIds":["record-1","record-1"],"acceptedRecordCount":0,
						 "acceptedBytes":-1,"acceptedAt":"2026-11-09T12:00:00+01:00","schemaVersion":2}
						"""))
				.andExpect(status().isBadRequest());

		org.mockito.Mockito.when(usageFacts.find("tenant-1", "product-1",
				java.time.Instant.parse("2026-11-01T00:00:00Z"), java.time.Instant.parse("2026-12-01T00:00:00Z")))
				.thenReturn(List.of());
		mockMvc.perform(get("/internal/usage-facts").header("X-LhamaLog-Service-Token", TOKEN)
				.param("tenantId", "tenant-1").param("productId", "product-1")
				.param("from", "2026-11-01T00:00:00Z").param("to", "2026-12-01T00:00:00Z"))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		mockMvc.perform(get("/internal/usage-facts").header("X-LhamaLog-Service-Token", TOKEN)
				.param("tenantId", "tenant-1").param("productId", "product-1")
				.param("from", "2026-12-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
				.andExpect(status().isBadRequest());

		org.mockito.Mockito.when(usageFacts.currentCycle("tenant-1", "product-1"))
				.thenReturn(new UsageCycleSummary(java.time.Instant.parse("2026-11-01T00:00:00Z"),
						java.time.Instant.parse("2026-12-01T00:00:00Z"), 1, 1_000_000_000L, 1d));
		mockMvc.perform(get("/internal/usage-facts/current-cycle")
				.header("X-LhamaLog-Service-Token", TOKEN)
				.param("tenantId", "tenant-1").param("productId", "product-1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.acceptedGigabytes").value(1d));
	}
}
