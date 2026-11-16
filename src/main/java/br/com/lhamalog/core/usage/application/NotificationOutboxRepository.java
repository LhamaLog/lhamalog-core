package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.util.List;

/** Durable outbox port. Duplicate inserts mean the cycle threshold was already recorded. */
public interface NotificationOutboxRepository {
	boolean saveIfAbsent(LowBalanceNotification notification);

	List<LowBalanceNotification> findPending();

	boolean markPublished(String idempotencyKey, Instant publishedAt);
}
