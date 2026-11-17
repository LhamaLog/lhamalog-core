package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.util.List;

/** The notification payload persisted before publishing to the notification stream. */
public record LowBalanceNotification(String idempotencyKey, String tenantId, String productId, Instant cycleStart,
		int threshold, long remainingBytes, String type, String severity, List<String> mandatoryRoles,
		List<String> mandatoryChannels, NotificationOutboxState state, Instant createdAt, Instant publishedAt) {

	public static LowBalanceNotification pending(String tenantId, String productId, Instant cycleStart, int threshold,
			long remainingBytes, Instant createdAt) {
		return new LowBalanceNotification(tenantId + "+" + productId + "+" + cycleStart + "+" + threshold, tenantId,
				productId, cycleStart, threshold, remainingBytes, "SALDO_BAIXO", "MEDIUM",
				List.of("DONO", "ADMINISTRADOR"), List.of("WEB", "EMAIL"), NotificationOutboxState.PENDING, createdAt, null);
	}

	public LowBalanceNotification publishedAt(Instant publishedAt) {
		return new LowBalanceNotification(idempotencyKey, tenantId, productId, cycleStart, threshold, remainingBytes, type,
				severity, mandatoryRoles, mandatoryChannels, NotificationOutboxState.PUBLISHED, createdAt, publishedAt);
	}
}
