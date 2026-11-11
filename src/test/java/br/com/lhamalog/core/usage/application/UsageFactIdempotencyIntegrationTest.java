package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.infrastructure.InMemoryUsageFactRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageFactIdempotencyIntegrationTest {

	@Test
	void sameFactIsRecordedOnceButAConflictingBodyIsRejected() {
		InMemoryUsageFactRepository repository = new InMemoryUsageFactRepository();
		UsageFactService service = new UsageFactService(repository, new InMemoryPlanSubscriptionRepository(),
				() -> Instant.parse("2026-11-09T12:00:00Z"));
		UsageFactRequest fact = fact("fact-1", "batch-1", 500);

		assertThat(service.record(fact)).isEqualTo(UsageFactService.RecordResult.CREATED);
		assertThat(service.record(fact)).isEqualTo(UsageFactService.RecordResult.DUPLICATE);
		assertThat(service.record(fact("fact-1", "batch-1", 600)))
				.isEqualTo(UsageFactService.RecordResult.CONFLICT);
		assertThat(repository.findAll()).hasSize(1);
	}

	private static UsageFactRequest fact(String factId, String batchId, long bytes) {
		return new UsageFactRequest(factId, batchId, "tenant-1", "product-1", UsageDomain.LOGS,
				List.of("record-1"), 1, bytes, OffsetDateTime.parse("2026-11-09T10:00:00Z"), 1);
	}
}
