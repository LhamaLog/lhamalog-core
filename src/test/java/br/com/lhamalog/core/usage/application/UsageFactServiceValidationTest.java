package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.lhamalog.core.usage.infrastructure.InMemoryUsageFactRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UsageFactServiceValidationTest {

	@Test
	void rejectsAWindowThatIsNotStrictlyAscending() {
		UsageFactService service = new UsageFactService(new InMemoryUsageFactRepository(),
				new InMemoryPlanSubscriptionRepository());
		Instant from = Instant.parse("2026-12-01T00:00:00Z");
		Instant to = Instant.parse("2026-11-01T00:00:00Z");

		assertThatThrownBy(() -> service.find("tenant-1", "product-1", from, to, 10, null))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThatThrownBy(() -> service.find("tenant-1", "product-1", from, from, null, null))
				.isInstanceOf(ResponseStatusException.class);
	}
}
