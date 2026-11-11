package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.application.ReservationState;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("usage_quota_reservations")
public record QuotaReservationDocument(@Id String reservationId, String tenantId, String productId,
		Instant cycleStart, long requestedBytes, UsageDomain domain, String batchId, boolean unlimited,
		ReservationState state, Instant createdAt, Instant expiresAt, Instant updatedAt) {
}
