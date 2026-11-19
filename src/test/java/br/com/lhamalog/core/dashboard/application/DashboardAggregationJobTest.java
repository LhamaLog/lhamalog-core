package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.lhamalog.core.dashboard.application.DashboardAggregateRepository.ProductScope;
import br.com.lhamalog.core.logging.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationJobTest {

	@Mock OpenObserveLogQueryPort openObserve;

	InMemoryDashboardAggregateRepository aggregates;
	Clock clock;
	DashboardAggregationJob job;

	@BeforeEach
	void setUp() {
		aggregates = new InMemoryDashboardAggregateRepository();
		clock = Clock.fixed(Instant.parse("2026-11-18T15:30:00Z"), ZoneOffset.UTC);
		job = new DashboardAggregationJob(aggregates, openObserve, clock);
		aggregates.setScopes(List.of(new ProductScope("tenant-1", "prd-1")));
	}

	@Test
	void upsertsBucketsAndAdvancesWatermark() {
		Instant from = Instant.parse("2026-11-18T14:00:00Z");
		Instant currentHour = Instant.parse("2026-11-18T15:00:00Z");
		when(openObserve.countLogs(eq("tenant-1"), eq("prd-1"), eq(from), eq(currentHour),
				eq(EnumSet.allOf(LogSeverity.class))))
				.thenReturn(List.of(
						new LogCountBucket(Instant.parse("2026-11-18T14:00:00Z"), LogSeverity.ERROR, "checkout", 5),
						new LogCountBucket(Instant.parse("2026-11-18T14:00:00Z"), LogSeverity.INFO, "checkout", 10)));

		job.run();

		assertEquals(2, aggregates.all().size());
		assertEquals(currentHour, aggregates.findWatermark("tenant-1").orElseThrow());
	}

	@Test
	void failureDoesNotAdvanceWatermarkButKeepsPriorAggregates() {
		Instant prior = Instant.parse("2026-11-18T13:00:00Z");
		aggregates.saveWatermark("tenant-1", prior, prior);
		aggregates.upsert(new LogCountBucket(Instant.parse("2026-11-18T12:00:00Z"), LogSeverity.ERROR, "old", 1),
				"tenant-1", "prd-1", prior);
		when(openObserve.countLogs(any(), any(), any(), any(), any()))
				.thenThrow(new RuntimeException("openobserve down"));

		job.run();

		assertEquals(prior, aggregates.findWatermark("tenant-1").orElseThrow());
		assertEquals(1, aggregates.all().size());
	}

	@Test
	void setsCorrelationIdDuringExecution() {
		when(openObserve.countLogs(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			assertTrue(CorrelationId.isValid(MDC.get(CorrelationId.KEY)));
			return List.of();
		});
		job.run();
		assertEquals(null, MDC.get(CorrelationId.KEY));
	}
}
