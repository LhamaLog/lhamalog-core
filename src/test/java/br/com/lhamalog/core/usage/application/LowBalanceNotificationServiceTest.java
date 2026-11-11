package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LowBalanceNotificationServiceTest {

	@Test
	void recordsBothThresholdsOnceWhenOneConfirmationCrossesBoth() {
		MemoryOutbox outbox = new MemoryOutbox();
		List<LowBalanceNotification> published = new ArrayList<>();
		LowBalanceNotificationService service = new LowBalanceNotificationService(outbox, Optional.of(published::add));
		var balance = new QuotaReservationRepository.CycleBalanceSnapshot(10_000, 0, 9_600,
				Instant.parse("2026-11-01T00:00:00Z"));

		service.recordCrossedThresholds("tenant", "product", balance, Instant.parse("2026-11-10T12:00:00Z"));
		service.recordCrossedThresholds("tenant", "product", balance, Instant.parse("2026-11-10T12:01:00Z"));

		assertThat(outbox.values).extracting(LowBalanceNotification::threshold).containsExactlyInAnyOrder(20, 5);
		assertThat(published).hasSize(2).allSatisfy(event -> {
			assertThat(event.type()).isEqualTo("SALDO_BAIXO");
			assertThat(event.severity()).isEqualTo("MEDIUM");
			assertThat(event.remainingBytes()).isEqualTo(400);
			assertThat(event.mandatoryRoles()).containsExactly("DONO", "ADMINISTRADOR");
			assertThat(event.mandatoryChannels()).containsExactly("WEB", "EMAIL");
		});
	}

	private static final class MemoryOutbox implements NotificationOutboxRepository {
		private final List<LowBalanceNotification> values = new ArrayList<>();

		@Override public boolean saveIfAbsent(LowBalanceNotification notification) {
			if (values.stream().anyMatch(value -> value.idempotencyKey().equals(notification.idempotencyKey()))) return false;
			values.add(notification);
			return true;
		}

		@Override public List<LowBalanceNotification> findPending() {
			return values.stream().filter(value -> value.state() == NotificationOutboxState.PENDING).toList();
		}

		@Override public boolean markPublished(String idempotencyKey, Instant publishedAt) {
			for (int index = 0; index < values.size(); index++) {
				if (values.get(index).idempotencyKey().equals(idempotencyKey)
						&& values.get(index).state() == NotificationOutboxState.PENDING) {
					values.set(index, values.get(index).publishedAt(publishedAt));
					return true;
				}
			}
			return false;
		}
	}
}
