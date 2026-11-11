package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Atomic persistence port for reservations and their per-cycle balance. */
public interface QuotaReservationRepository {
	Optional<QuotaReservation> find(String reservationId);

	boolean reserve(CycleBalance balance, QuotaReservation reservation);

	boolean saveUnlimited(QuotaReservation reservation);

	boolean transition(String reservationId, ReservationState expected, ReservationState next, Instant now);

	boolean moveReservedToConsumed(QuotaReservation reservation, Instant now);

	boolean releaseReserved(QuotaReservation reservation, ReservationState next, Instant now);

	boolean refundConsumed(QuotaReservation reservation, Instant now);

	List<QuotaReservation> findExpiredReserved(Instant now);

	Optional<CycleBalanceSnapshot> findBalance(String tenantId, String productId, Instant cycleStart);

	record CycleBalance(String tenantId, String productId, Instant cycleStart, long allowanceBytes) {
		public CycleBalance {
			if (allowanceBytes < 1) {
				throw new IllegalArgumentException("allowanceBytes must be positive");
			}
		}
	}

	record CycleBalanceSnapshot(long allowanceBytes, long reservedBytes, long consumedBytes, Instant cycleStart) {
		public long remainingBytes() {
			return allowanceBytes - reservedBytes - consumedBytes;
		}
	}
}
