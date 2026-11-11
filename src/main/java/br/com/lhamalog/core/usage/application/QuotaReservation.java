package br.com.lhamalog.core.usage.application;

import br.com.lhamalog.core.usage.api.UsageDomain;
import java.time.Instant;
import java.util.Objects;

/** Persisted reservation and the immutable values needed for an exactly-once balance transition. */
public record QuotaReservation(String reservationId, String tenantId, String productId, Instant cycleStart,
		long requestedBytes, UsageDomain domain, String batchId, boolean unlimited, ReservationState state,
		Instant createdAt, Instant expiresAt, Instant updatedAt) {

	public QuotaReservation {
		Objects.requireNonNull(reservationId, "reservationId");
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(productId, "productId");
		Objects.requireNonNull(cycleStart, "cycleStart");
		Objects.requireNonNull(domain, "domain");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (requestedBytes <= 0) {
			throw new IllegalArgumentException("requestedBytes must be positive");
		}
	}

	public QuotaReservation withState(ReservationState next, Instant now) {
		return new QuotaReservation(reservationId, tenantId, productId, cycleStart, requestedBytes, domain, batchId,
				unlimited, next, createdAt, expiresAt, now);
	}
}
