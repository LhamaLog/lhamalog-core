package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageAggregationServiceTest {

	@Test
	void aggregatesNewBatchesInTheCurrentWeeklyCycleUsingDecimalGigabytes() {
		UsageFactRepository repository = new UsageFactRepository.InMemory();
		PlanSubscriptionRepository.InMemory subscriptions = new PlanSubscriptionRepository.InMemory();
		subscriptions.save(new PlanSubscription(null, "tenant-1", "product-1", PlanType.WEEKLY,
				LocalDate.of(2026, 11, 2)));
		UsageFactService service = new UsageFactService(repository, subscriptions,
				() -> Instant.parse("2026-11-09T12:00:00Z"));

		service.record(fact("fact-1", "batch-1", Instant.parse("2026-11-08T12:00:00Z"), 1_000_000_000L));
		service.record(fact("fact-2", "batch-2", Instant.parse("2026-11-09T00:00:00Z"), 500_000_000L));

		UsageCycleSummary summary = service.currentCycle("tenant-1", "product-1");

		assertThat(summary.from()).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
		assertThat(summary.to()).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
		assertThat(summary.acceptedRecordCount()).isEqualTo(1);
		assertThat(summary.acceptedBytes()).isEqualTo(500_000_000L);
		assertThat(summary.acceptedGigabytes()).isEqualTo(0.5d);
	}

	@Test
	void monthlyAndSemiannualPlansUseMonthlyCyclesAnchoredAtFirstPaymentInUtc() {
		assertThat(UsageCycle.calculate(PlanType.MONTHLY, LocalDate.of(2026, 1, 31),
				Instant.parse("2026-02-28T12:00:00Z")))
				.isEqualTo(new UsageCycle(Instant.parse("2026-02-28T00:00:00Z"),
						Instant.parse("2026-03-31T00:00:00Z")));
		assertThat(UsageCycle.calculate(PlanType.SEMIANNUAL, LocalDate.of(2026, 1, 31),
				Instant.parse("2026-03-15T12:00:00Z")))
				.isEqualTo(new UsageCycle(Instant.parse("2026-02-28T00:00:00Z"),
						Instant.parse("2026-03-31T00:00:00Z")));
	}

	private static UsageFactRequest fact(String factId, String batchId, Instant acceptedAt, long bytes) {
		return new UsageFactRequest(factId, batchId, "tenant-1", "product-1", UsageDomain.LOGS,
				List.of(factId + "-record"), 1, bytes, acceptedAt.atOffset(java.time.ZoneOffset.UTC), 1);
	}
}
