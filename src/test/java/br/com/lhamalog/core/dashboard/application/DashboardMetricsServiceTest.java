package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.CompanyMetrics;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductMetrics;
import br.com.lhamalog.core.dashboard.application.UsageMetricsPort.UsageSnapshot;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DashboardMetricsServiceTest {

	@Mock ProductService products;
	@Mock OpenObserveLogQueryPort openObserve;
	@Mock UsageMetricsPort usageMetrics;

	InMemoryDashboardAggregateRepository aggregates;
	DashboardMetricsService service;
	CompanySession admin;
	CompanySession member;

	@BeforeEach
	void setUp() {
		aggregates = new InMemoryDashboardAggregateRepository();
		service = new DashboardMetricsService(products, aggregates, openObserve, usageMetrics);
		admin = new CompanySession("tenant-1", "admin-1", true);
		member = new CompanySession("tenant-1", "member-1", false);
	}

	@Test
	void returnsZerosWhenNoData() {
		Product product = product("prd-1", "Checkout");
		when(products.get(admin, "prd-1")).thenReturn(product);
		when(usageMetrics.retentionDays("tenant-1")).thenReturn(30);
		when(usageMetrics.forProduct(eq("tenant-1"), eq("prd-1"), any(), any()))
				.thenReturn(new UsageSnapshot(0, 0, 1L << 30, 30));
		when(openObserve.countLogs(any(), any(), any(), any(), any())).thenReturn(List.of());

		Instant from = Instant.parse("2026-11-11T00:00:00Z");
		Instant to = Instant.parse("2026-11-12T00:00:00Z");
		ProductMetrics metrics = service.productMetrics(admin, "prd-1", from, to, null);

		assertEquals(0, metrics.totals().logs());
		assertEquals(0, metrics.totals().error());
		assertTrue(metrics.series().points().isEmpty());
		assertTrue(metrics.serviceRanking().isEmpty());
		assertEquals(1L << 30, metrics.volumeIngestaoBytes());
		assertEquals("hour", metrics.series().bucket());
	}

	@Test
	void filtersSeveritiesAndMergesHybridWindow() {
		Product product = product("prd-1", "Checkout");
		when(products.get(admin, "prd-1")).thenReturn(product);
		when(usageMetrics.retentionDays("tenant-1")).thenReturn(30);
		when(usageMetrics.forProduct(eq("tenant-1"), eq("prd-1"), any(), any()))
				.thenReturn(new UsageSnapshot(100, 3, 10L << 30, 30));

		Instant from = Instant.parse("2026-11-18T10:00:00Z");
		Instant to = Instant.parse("2026-11-18T13:00:00Z");
		Instant watermark = Instant.parse("2026-11-18T12:00:00Z");
		aggregates.saveWatermark("tenant-1", watermark, watermark);
		aggregates.upsert(new LogCountBucket(Instant.parse("2026-11-18T10:00:00Z"), LogSeverity.ERROR, "checkout", 4),
				"tenant-1", "prd-1", watermark);
		aggregates.upsert(new LogCountBucket(Instant.parse("2026-11-18T11:00:00Z"), LogSeverity.INFO, "checkout", 9),
				"tenant-1", "prd-1", watermark);
		when(openObserve.countLogs(eq("tenant-1"), eq("prd-1"), eq(watermark), eq(to),
				eq(EnumSet.of(LogSeverity.ERROR, LogSeverity.WARNING))))
				.thenReturn(List.of(new LogCountBucket(Instant.parse("2026-11-18T12:00:00Z"), LogSeverity.ERROR,
						"payments", 2)));

		ProductMetrics metrics = service.productMetrics(admin, "prd-1", from, to, "ERROR,WARNING");

		assertEquals(6, metrics.totals().logs());
		assertEquals(6, metrics.totals().error());
		assertEquals(0, metrics.totals().info());
		assertEquals(3, metrics.totals().traces());
		assertEquals(100, metrics.totals().volumeUsedBytes());
		assertEquals(watermark, metrics.dadosConsolidadosAte());
		assertEquals(2, metrics.serviceRanking().size());
		assertEquals("checkout", metrics.serviceRanking().get(0).serviceName());
		assertEquals(4, metrics.serviceRanking().get(0).error());
		assertEquals("payments", metrics.serviceRanking().get(1).serviceName());
	}

	@Test
	void rejectsRangeAboveRetentionCap() {
		Instant from = Instant.parse("2026-10-01T00:00:00Z");
		Instant to = Instant.parse("2026-10-20T00:00:00Z");
		ApiStatusException exception = assertThrows(ApiStatusException.class,
				() -> DashboardMetricsService.validateRange(from, to, 7));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertEquals("invalid_filter", exception.code());
	}

	@Test
	void rejectsUnknownSeverity() {
		ApiStatusException exception = assertThrows(ApiStatusException.class,
				() -> DashboardMetricsService.parseSeverities("ERROR,FATAL"));
		assertEquals("invalid_filter", exception.code());
	}

	@Test
	void companyMetricsConsolidatesAuthorizedProductsOnly() {
		Product first = product("prd-1", "Checkout");
		Product second = product("prd-2", "Billing");
		when(products.list(admin)).thenReturn(List.of(first, second));
		when(usageMetrics.retentionDays("tenant-1")).thenReturn(30);
		when(openObserve.countLogs(any(), any(), any(), any(), any())).thenReturn(List.of(
				new LogCountBucket(Instant.parse("2026-11-18T10:00:00Z"), LogSeverity.ERROR, "svc", 1)));

		Instant from = Instant.parse("2026-11-18T10:00:00Z");
		Instant to = Instant.parse("2026-11-18T11:00:00Z");
		CompanyMetrics metrics = service.companyMetrics(admin, from, to, null, null);

		assertEquals(2, metrics.totals().productsMonitored());
		assertEquals(2, metrics.totals().error());
		assertEquals(2, metrics.products().size());
	}

	@Test
	void memberWithoutProductsGetsEmptyCompanyMetrics() {
		when(products.list(member)).thenReturn(List.of());
		when(usageMetrics.retentionDays("tenant-1")).thenReturn(15);
		Instant from = Instant.parse("2026-11-18T10:00:00Z");
		Instant to = Instant.parse("2026-11-18T11:00:00Z");
		CompanyMetrics metrics = service.companyMetrics(member, from, to, null, null);
		assertEquals(0, metrics.totals().productsMonitored());
		assertEquals(0, metrics.totals().logs());
		assertTrue(metrics.products().isEmpty());
		assertTrue(metrics.seriesByProduct().isEmpty());
	}

	@Test
	void rejectsInaccessibleProductIdsInCompanyFilter() {
		when(products.list(member)).thenReturn(List.of(product("prd-1", "Checkout")));
		when(usageMetrics.retentionDays("tenant-1")).thenReturn(30);
		Instant from = Instant.parse("2026-11-18T10:00:00Z");
		Instant to = Instant.parse("2026-11-18T11:00:00Z");
		ApiStatusException exception = assertThrows(ApiStatusException.class,
				() -> service.companyMetrics(member, from, to, "prd-other", null));
		assertEquals("invalid_filter", exception.code());
	}

	@Test
	void productNotFoundPropagatesAs404() {
		when(products.get(member, "prd-x")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
				"Produto não encontrado ou sem acesso."));
		Instant from = Instant.parse("2026-11-18T10:00:00Z");
		Instant to = Instant.parse("2026-11-18T11:00:00Z");
		assertThrows(ResponseStatusException.class,
				() -> service.productMetrics(member, "prd-x", from, to, null));
	}

	private static Product product(String id, String name) {
		return new Product(id, "tenant-1", name, Product.normalizeName(name), null, null, null, "ACTIVE",
				Instant.parse("2026-01-01T00:00:00Z"), null, null, 1L);
	}
}
