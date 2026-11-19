package br.com.lhamalog.core.dashboard.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lhamalog.core.dashboard.application.LogCountBucket;
import br.com.lhamalog.core.dashboard.application.LogSeverity;
import br.com.lhamalog.core.dashboard.application.OpenObserveLogQueryPort;
import br.com.lhamalog.core.dashboard.infrastructure.DashboardAggregationWatermarkDocument;
import br.com.lhamalog.core.dashboard.infrastructure.DashboardLogHourlyAggregateDocument;
import br.com.lhamalog.core.logging.CorrelationId;
import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanTier;
import br.com.lhamalog.core.usage.application.PlanType;
import br.com.lhamalog.core.usage.application.SubscriptionStatus;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository.StoredUsageFact;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@CoreIntegrationTest
class DashboardMetricsControllerIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	@Autowired UsageFactRepository usageFacts;

	@MockitoBean OpenObserveLogQueryPort openObserve;

	private TestTenants tenants;

	@BeforeEach
	void cleanDashboardCollections() {
		mongo.dropCollection(DashboardLogHourlyAggregateDocument.class);
		mongo.dropCollection(DashboardAggregationWatermarkDocument.class);
	}

	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private RequestPostProcessor session(String organization, String user, String role) {
		return tenants().session(organization, user, role);
	}

	private String organization() {
		return "org_dash_" + UUID.randomUUID();
	}

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode createProduct(RequestPostProcessor admin, String name) throws Exception {
		return json(mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"context\":{}}")).andExpect(status().isCreated()));
	}

	@Test
	void productMetricsReturns200WithContractFieldsAndEchoesCorrelationId() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		String tenantId = tenants().tenantId(organization);
		String productId = createProduct(admin, "Checkout").get("id").asText();
		seedPlan(tenantId, productId, PlanTier.PRO, BigDecimal.TEN);
		seedUsage(tenantId, productId);
		Instant watermark = Instant.parse("2026-11-18T12:00:00Z");
		mongo.save(new DashboardAggregationWatermarkDocument(tenantId, watermark, watermark));
		mongo.save(new DashboardLogHourlyAggregateDocument(
				DashboardLogHourlyAggregateDocument.composeId(tenantId, productId, "ERROR", "checkout",
						Instant.parse("2026-11-18T10:00:00Z")),
				tenantId, productId, "ERROR", "checkout", Instant.parse("2026-11-18T10:00:00Z"), 4, watermark, 1));
		when(openObserve.countLogs(eq(tenantId), eq(productId), eq(watermark),
				eq(Instant.parse("2026-11-18T14:00:00Z")), any()))
				.thenReturn(List.of(new LogCountBucket(Instant.parse("2026-11-18T12:00:00Z"), LogSeverity.WARNING,
						"checkout", 3)));

		String cid = "dash-cid-product-1";
		ResultActions result = mvc.perform(get("/api/dashboard/products/" + productId + "/metrics")
				.with(admin)
				.header(CorrelationId.HEADER, cid)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T14:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(header().string(CorrelationId.HEADER, cid))
				.andExpect(jsonPath("$.productId").value(productId))
				.andExpect(jsonPath("$.retentionDays").value(30))
				.andExpect(jsonPath("$.volumeIngestaoBytes").value(10L << 30))
				.andExpect(jsonPath("$.totals.error").value(4))
				.andExpect(jsonPath("$.totals.warning").value(3))
				.andExpect(jsonPath("$.totals.traces").value(2))
				.andExpect(jsonPath("$.totals.volumeUsedBytes").value(100))
				.andExpect(jsonPath("$.dadosConsolidadosAte").value("2026-11-18T12:00:00Z"))
				.andExpect(jsonPath("$.series.bucket").value("hour"))
				.andExpect(jsonPath("$.serviceRanking[0].serviceName").value("checkout"));

		String body = result.andReturn().getResponse().getContentAsString();
		assertFalse(body.contains("occurrences"));
		assertFalse(body.contains("selo"));
		assertFalse(body.contains("Crítico"));
	}

	@Test
	void productMetricsRejectsInvalidSeverityAndOverlongRange() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		String tenantId = tenants().tenantId(organization);
		String productId = createProduct(admin, "Billing").get("id").asText();
		seedPlan(tenantId, productId, PlanTier.FREE, BigDecimal.ONE);

		mvc.perform(get("/api/dashboard/products/" + productId + "/metrics").with(admin)
				.param("from", "2026-11-01T00:00:00Z")
				.param("to", "2026-11-02T00:00:00Z")
				.param("severities", "FATAL"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_filter"));

		mvc.perform(get("/api/dashboard/products/" + productId + "/metrics").with(admin)
				.param("from", "2026-10-01T00:00:00Z")
				.param("to", "2026-10-20T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_filter"));
	}

	@Test
	void productMetricsReturns404ForInaccessibleOrForeignProduct() throws Exception {
		String organization = organization();
		String otherOrg = organization();
		var admin = session(organization, "admin_1", "admin");
		var otherAdmin = session(otherOrg, "admin_2", "admin");
		var member = session(organization, "member_1", "member");
		String productId = createProduct(admin, "Private").get("id").asText();
		String foreignId = createProduct(otherAdmin, "Foreign").get("id").asText();

		mvc.perform(get("/api/dashboard/products/" + productId + "/metrics").with(member)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/dashboard/products/" + foreignId + "/metrics").with(admin)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/dashboard/products/does-not-exist/metrics").with(admin)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isNotFound());
	}

	@Test
	void companyMetricsConsolidatesAdminProductsAndAllowsEmptyMember() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		var member = session(organization, "member_1", "member");
		String tenantId = tenants().tenantId(organization);
		String first = createProduct(admin, "Alpha").get("id").asText();
		String second = createProduct(admin, "Beta").get("id").asText();
		seedPlan(tenantId, first, PlanTier.STARTER, BigDecimal.valueOf(5));
		when(openObserve.countLogs(any(), any(), any(), any(), any())).thenReturn(List.of(
				new LogCountBucket(Instant.parse("2026-11-18T10:00:00Z"), LogSeverity.ERROR, "svc", 1)));

		mvc.perform(get("/api/dashboard/company/metrics").with(admin)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totals.productsMonitored").value(2))
				.andExpect(jsonPath("$.products.length()").value(2));

		mvc.perform(get("/api/dashboard/company/metrics").with(member)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totals.productsMonitored").value(0))
				.andExpect(jsonPath("$.products.length()").value(0));

		mongo.insert(new ProductAccess(first + ":member_1", tenantId, first, "member_1"));
		mvc.perform(get("/api/dashboard/company/metrics").with(member)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z")
				.param("productIds", first + "," + second))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_filter"));

		JsonNode filtered = json(mvc.perform(get("/api/dashboard/company/metrics").with(member)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z")
				.param("productIds", first))
				.andExpect(status().isOk()));
		assertEquals(1, filtered.get("totals").get("productsMonitored").asInt());
		assertEquals(first, filtered.get("products").get(0).get("productId").asText());
	}

	@Test
	void ownerAndAdminShareTheSameProductVisibilityAndUnauthorizedIs401() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		var owner = session(organization, "owner_1", "admin");
		String productId = createProduct(admin, "Shared").get("id").asText();
		when(openObserve.countLogs(any(), any(), any(), any(), any())).thenReturn(List.of());

		mvc.perform(get("/api/dashboard/products/" + productId + "/metrics").with(owner)
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(productId));

		mvc.perform(get("/api/dashboard/company/metrics")
				.param("from", "2026-11-18T10:00:00Z")
				.param("to", "2026-11-18T11:00:00Z"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void openObserveFailureOnOpenIntervalReturns5xxWithoutCorruptingAggregates() throws Exception {
		String organization = organization();
		var admin = session(organization, "admin_1", "admin");
		String tenantId = tenants().tenantId(organization);
		String productId = createProduct(admin, "Fragile").get("id").asText();
		seedPlan(tenantId, productId, PlanTier.PRO, BigDecimal.ONE);
		Instant watermark = Instant.parse("2026-11-18T12:00:00Z");
		mongo.save(new DashboardAggregationWatermarkDocument(tenantId, watermark, watermark));
		mongo.save(new DashboardLogHourlyAggregateDocument(
				DashboardLogHourlyAggregateDocument.composeId(tenantId, productId, "ERROR", "svc",
						Instant.parse("2026-11-18T11:00:00Z")),
				tenantId, productId, "ERROR", "svc", Instant.parse("2026-11-18T11:00:00Z"), 7, watermark, 1));
		when(openObserve.countLogs(any(), any(), any(), any(), any()))
				.thenThrow(new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.BAD_GATEWAY, "Falha ao consultar métricas de logs."));

		mvc.perform(get("/api/dashboard/products/" + productId + "/metrics").with(admin)
				.param("from", "2026-11-18T11:00:00Z")
				.param("to", "2026-11-18T14:00:00Z"))
				.andExpect(status().isBadGateway());

		assertTrue(mongo.findById(tenantId, DashboardAggregationWatermarkDocument.class) != null);
		assertEquals(1, mongo.findAll(DashboardLogHourlyAggregateDocument.class).stream()
				.filter(document -> productId.equals(document.productId())).count());
	}

	private void seedPlan(String tenantId, String productId, PlanTier tier, BigDecimal allowanceGib) {
		mongo.save(new PlanSubscription(UUID.randomUUID().toString(), tenantId, productId, PlanType.MONTHLY,
				LocalDate.of(2026, 1, 1), allowanceGib, false, SubscriptionStatus.ACTIVE, false, null, tier));
	}

	private void seedUsage(String tenantId, String productId) {
		usageFacts.saveIfAbsent(new StoredUsageFact(new UsageFactRequest(
				UUID.randomUUID().toString(), "batch-1", tenantId, productId, UsageDomain.LOGS,
				List.of("log-1"), 1, 100, OffsetDateTime.parse("2026-11-18T11:00:00Z"), 1),
				Instant.parse("2026-11-18T11:00:01Z")));
		usageFacts.saveIfAbsent(new StoredUsageFact(new UsageFactRequest(
				UUID.randomUUID().toString(), "batch-2", tenantId, productId, UsageDomain.TRACES,
				List.of("tr-1", "tr-2"), 2, 0, OffsetDateTime.parse("2026-11-18T11:30:00Z"), 1),
				Instant.parse("2026-11-18T11:30:01Z")));
	}
}
