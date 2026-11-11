package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.NotificationOutboxState;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("usage_notification_outbox")
public record NotificationOutboxDocument(@Id String idempotencyKey, String tenantId, String productId, Instant cycleStart,
		int threshold, long remainingBytes, String type, String severity, List<String> mandatoryRoles,
		List<String> mandatoryChannels, NotificationOutboxState state, Instant createdAt, Instant publishedAt) {
}
