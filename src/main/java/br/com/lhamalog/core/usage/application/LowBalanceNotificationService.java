package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LowBalanceNotificationService {
	private static final Logger log = LoggerFactory.getLogger(LowBalanceNotificationService.class);

	private final NotificationOutboxRepository outbox;
	private final Optional<LowBalanceNotificationPublisher> publisher;

	public LowBalanceNotificationService(NotificationOutboxRepository outbox,
			Optional<LowBalanceNotificationPublisher> publisher) {
		this.outbox = outbox;
		this.publisher = publisher;
	}

	public void recordCrossedThresholds(String tenantId, String productId,
			QuotaReservationRepository.CycleBalanceSnapshot balance, Instant now) {
		for (int threshold : new int[] {20, 5}) {
			if (isAtOrBelowThreshold(balance, threshold)) {
				outbox.saveIfAbsent(LowBalanceNotification.pending(tenantId, productId, balance.cycleStart(), threshold,
						balance.remainingBytes(), now));
			}
		}
		publishPending();
	}

	public void publishPending() {
		if (publisher.isEmpty()) {
			return;
		}
		for (LowBalanceNotification notification : outbox.findPending()) {
			try {
				publisher.get().publish(notification);
				outbox.markPublished(notification.idempotencyKey(), Instant.now());
			}
			catch (RuntimeException failure) {
				// Durable outbox keeps PENDING; scheduled retry republishes. Never fail confirm after debit.
				log.warn("SALDO_BAIXO publish deferred for key={} threshold={}", notification.idempotencyKey(),
						notification.threshold(), failure);
			}
		}
	}

	private static boolean isAtOrBelowThreshold(QuotaReservationRepository.CycleBalanceSnapshot balance, int threshold) {
		return java.math.BigInteger.valueOf(balance.remainingBytes()).multiply(java.math.BigInteger.valueOf(100))
				.compareTo(java.math.BigInteger.valueOf(balance.allowanceBytes()).multiply(java.math.BigInteger.valueOf(threshold))) <= 0;
	}
}
