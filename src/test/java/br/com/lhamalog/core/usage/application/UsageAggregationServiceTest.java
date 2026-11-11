package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.infrastructure.InMemoryUsageFactRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageAggregationServiceTest {

	@Test
	void aggregatesNewBatchesInTheCurrentWeeklyCycleUsingDecimalGigabytes() {
		InMemoryUsageFactRepository repository = new InMemoryUsageFactRepository();
		InMemoryPlanSubscriptionRepository subscriptions = new InMemoryPlanSubscriptionRepository();
		subscriptions.save(new PlanSubscription(null, "tenant-1", "product-1", PlanType.WEEKLY,
				LocalDate.of(2026, 11, 2)));
		UsageFactService service = new UsageFactService(repository, subscriptions,
				() -> Instant.parse("2026-11-09T12:00:00Z"));

		service.record(fact("fact-1", "batch-1", UsageDomain.LOGS, Instant.parse("2026-11-08T12:00:00Z"), 1_000_000_000L));
		service.record(fact("fact-2", "batch-2", UsageDomain.LOGS, Instant.parse("2026-11-09T00:00:00Z"), 500_000_000L));
		service.record(fact("fact-3", "batch-3", UsageDomain.TRACES, Instant.parse("2026-11-09T01:00:00Z"), 250_000_000L));

		UsageCycleSummary summary = service.currentCycle("tenant-1", "product-1");

		assertThat(summary.active()).isTrue();
		assertThat(summary.from()).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
		assertThat(summary.to()).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
		assertThat(summary.logs().acceptedRecordCount()).isEqualTo(1);
		assertThat(summary.logs().acceptedBytes()).isEqualTo(500_000_000L);
		assertThat(summary.logs().acceptedGigabytes()).isEqualTo(0.5d);
		assertThat(summary.traces().acceptedRecordCount()).isEqualTo(1);
		assertThat(summary.traces().acceptedBytes()).isEqualTo(250_000_000L);
		assertThat(summary.traces().acceptedGigabytes()).isEqualTo(0.25d);
	}

	@Test
	void monthlyAndSemiannualPlansUseMonthlyCyclesAnchoredAtFirstPaymentInUtc() {
		assertThat(UsageCycle.calculate(PlanType.MONTHLY, LocalDate.of(2026, 1, 31),
				Instant.parse("2026-02-28T12:00:00Z")))
				.contains(new UsageCycle(Instant.parse("2026-02-28T00:00:00Z"),
						Instant.parse("2026-03-31T00:00:00Z")));
		assertThat(UsageCycle.calculate(PlanType.SEMIANNUAL, LocalDate.of(2026, 1, 31),
				Instant.parse("2026-03-15T12:00:00Z")))
				.contains(new UsageCycle(Instant.parse("2026-02-28T00:00:00Z"),
						Instant.parse("2026-03-31T00:00:00Z")));
	}

	@Test
	void returnsNoActiveCycleBeforeFirstPaymentDate() {
		InMemoryPlanSubscriptionRepository subscriptions = new InMemoryPlanSubscriptionRepository();
		subscriptions.save(new PlanSubscription(null, "tenant-1", "product-1", PlanType.MONTHLY,
				LocalDate.of(2026, 12, 1)));
		UsageFactService service = new UsageFactService(new InMemoryUsageFactRepository(), subscriptions,
				() -> Instant.parse("2026-11-09T12:00:00Z"));

		UsageCycleSummary summary = service.currentCycle("tenant-1", "product-1");

		assertThat(UsageCycle.calculate(PlanType.MONTHLY, LocalDate.of(2026, 12, 1),
				Instant.parse("2026-11-09T12:00:00Z"))).isEmpty();
		assertThat(summary.active()).isFalse();
		assertThat(summary.from()).isNull();
		assertThat(summary.to()).isNull();
		assertThat(summary.logs().acceptedRecordCount()).isZero();
		assertThat(summary.traces().acceptedRecordCount()).isZero();
	}

	private static UsageFactRequest fact(String factId, String batchId, UsageDomain domain, Instant acceptedAt,
			long bytes) {
		return new UsageFactRequest(factId, batchId, "tenant-1", "product-1", domain,
				List.of(factId + "-record"), 1, bytes, acceptedAt.atOffset(java.time.ZoneOffset.UTC), 1);
	}
}
