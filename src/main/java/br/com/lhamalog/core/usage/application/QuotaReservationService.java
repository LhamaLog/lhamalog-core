package br.com.lhamalog.core.usage.application;

import br.com.lhamalog.core.usage.api.UsageDomain;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Coordinates idempotent quota transitions. Balance mutations are delegated to a repository because they
 * must remain conditional and atomic under concurrent exporters.
 */
@Service
public class QuotaReservationService {
	public enum Outcome { AUTHORIZED, AUTHORIZED_UNLIMITED, INSUFFICIENT_BALANCE, NO_PLAN }
	public enum TransitionResult { OK, NOT_FOUND, NOT_ACTIVE, NOT_REFUNDABLE }

	private static final BigDecimal BYTES_PER_DECIMAL_GIGABYTE = BigDecimal.valueOf(1_000_000_000L);
	private final PlanSubscriptionRepository subscriptions;
	private final QuotaReservationRepository reservations;
	private final InstantSource clock;
	private final Duration ttl;
	private final LowBalanceNotificationService lowBalanceNotifications;

	@Autowired
	public QuotaReservationService(PlanSubscriptionRepository subscriptions, QuotaReservationRepository reservations,
			QuotaReservationProperties properties, LowBalanceNotificationService lowBalanceNotifications) {
		this(subscriptions, reservations, InstantSource.system(), properties.ttl(), lowBalanceNotifications);
	}

	QuotaReservationService(PlanSubscriptionRepository subscriptions, QuotaReservationRepository reservations,
			InstantSource clock, Duration ttl) {
		this(subscriptions, reservations, clock, ttl, null);
	}

	QuotaReservationService(PlanSubscriptionRepository subscriptions, QuotaReservationRepository reservations,
			InstantSource clock, Duration ttl, LowBalanceNotificationService lowBalanceNotifications) {
		this.subscriptions = subscriptions;
		this.reservations = reservations;
		this.clock = clock;
		this.ttl = ttl;
		this.lowBalanceNotifications = lowBalanceNotifications;
	}

	public ReservationResponse reserve(ReservationRequest request) {
		Optional<QuotaReservation> previous = reservations.find(request.reservationId());
		if (previous.isPresent() && (previous.get().state() == ReservationState.RESERVED
				|| previous.get().state() == ReservationState.CONFIRMED)) {
			QuotaReservation value = previous.get();
			return new ReservationResponse(value.unlimited() ? Outcome.AUTHORIZED_UNLIMITED : Outcome.AUTHORIZED,
					value.reservationId(), value.expiresAt(), true);
		}
		Instant now = clock.instant();
		PlanSubscription subscription = subscriptions.findByTenantIdAndProductId(request.tenantId(), request.productId())
				.orElse(null);
		if (subscription == null || UsageCycle.calculate(subscription.planType(), subscription.firstPaymentDate(), now).isEmpty()
				|| (!subscription.unlimitedUsage() && subscription.allowanceGigabytes().signum() <= 0)) {
			return ReservationResponse.noPlan();
		}
		UsageCycle cycle = UsageCycle.calculate(subscription.planType(), subscription.firstPaymentDate(), now).orElseThrow();
		boolean unlimited = subscription.unlimitedUsage();
		QuotaReservation reservation = new QuotaReservation(request.reservationId(), request.tenantId(), request.productId(),
				cycle.from(), request.requestedBytes(), request.domain(), request.batchId(), unlimited, ReservationState.RESERVED,
				now, now.plus(ttl), now);
		boolean reserved = unlimited ? reservations.saveUnlimited(reservation)
				: reservations.reserve(new QuotaReservationRepository.CycleBalance(request.tenantId(), request.productId(),
						cycle.from(), allowanceBytes(subscription)), reservation);
		if (!reserved) {
			return unlimited ? ReservationResponse.unavailable() : ReservationResponse.insufficient();
		}
		return new ReservationResponse(unlimited ? Outcome.AUTHORIZED_UNLIMITED : Outcome.AUTHORIZED,
				reservation.reservationId(), reservation.expiresAt(), false);
	}

	public TransitionResult confirm(String reservationId) {
		Optional<QuotaReservation> reservation = reservations.find(reservationId);
		if (reservation.isEmpty()) return TransitionResult.NOT_FOUND;
		if (reservation.get().state() == ReservationState.CONFIRMED) return TransitionResult.OK;
		if (reservation.get().state() != ReservationState.RESERVED) return TransitionResult.NOT_ACTIVE;
		Instant now = clock.instant();
		if (!reservations.moveReservedToConsumed(reservation.get(), now)) return TransitionResult.NOT_ACTIVE;
		if (!reservation.get().unlimited() && lowBalanceNotifications != null) {
			reservations.findBalance(reservation.get().tenantId(), reservation.get().productId(), reservation.get().cycleStart())
					.ifPresent(balance -> lowBalanceNotifications.recordCrossedThresholds(reservation.get().tenantId(),
							reservation.get().productId(), balance, now));
		}
		return TransitionResult.OK;
	}

	public TransitionResult release(String reservationId) {
		Optional<QuotaReservation> reservation = reservations.find(reservationId);
		if (reservation.isEmpty()) return TransitionResult.NOT_FOUND;
		if (reservation.get().state() == ReservationState.RELEASED) return TransitionResult.OK;
		if (reservation.get().state() != ReservationState.RESERVED) return TransitionResult.NOT_ACTIVE;
		return reservations.releaseReserved(reservation.get(), ReservationState.RELEASED, clock.instant())
				? TransitionResult.OK : TransitionResult.NOT_ACTIVE;
	}

	public TransitionResult refund(String reservationId) {
		Optional<QuotaReservation> reservation = reservations.find(reservationId);
		if (reservation.isEmpty()) return TransitionResult.NOT_FOUND;
		if (reservation.get().state() == ReservationState.REFUNDED) return TransitionResult.OK;
		if (reservation.get().state() != ReservationState.CONFIRMED) return TransitionResult.NOT_REFUNDABLE;
		return reservations.refundConsumed(reservation.get(), clock.instant()) ? TransitionResult.OK : TransitionResult.NOT_REFUNDABLE;
	}

	public int expireReservations() {
		return reservations.findExpiredReserved(clock.instant()).stream()
				.filter(reservation -> reservations.releaseReserved(reservation, ReservationState.EXPIRED, clock.instant()))
				.mapToInt(ignored -> 1).sum();
	}

	public Optional<BalanceResponse> balance(String tenantId, String productId) {
		PlanSubscription subscription = subscriptions.findByTenantIdAndProductId(tenantId, productId).orElse(null);
		if (subscription == null || subscription.unlimitedUsage()) {
			return Optional.empty();
		}
		UsageCycle cycle = UsageCycle.calculate(subscription.planType(), subscription.firstPaymentDate(), clock.instant()).orElse(null);
		if (cycle == null) {
			return Optional.empty();
		}
		return Optional.of(reservations.findBalance(tenantId, productId, cycle.from())
				.map(value -> new BalanceResponse(value.allowanceBytes(), value.reservedBytes(), value.consumedBytes(),
						value.remainingBytes(), value.cycleStart()))
				.orElseGet(() -> {
					long allowance = allowanceBytes(subscription);
					return new BalanceResponse(allowance, 0, 0, allowance, cycle.from());
				}));
	}

	private static long allowanceBytes(PlanSubscription subscription) {
		try {
			return subscription.allowanceGigabytes().multiply(BYTES_PER_DECIMAL_GIGABYTE).longValueExact();
		}
		catch (ArithmeticException exception) {
			throw new IllegalArgumentException("allowance is outside the supported byte range", exception);
		}
	}

	public record ReservationRequest(String reservationId, String tenantId, String productId, long requestedBytes,
			String batchId, UsageDomain domain) {
		public ReservationRequest {
			if (reservationId == null || reservationId.isBlank() || tenantId == null || tenantId.isBlank()
					|| productId == null || productId.isBlank() || requestedBytes <= 0 || domain == null) {
				throw new IllegalArgumentException("invalid reservation request");
			}
		}
	}

	public record ReservationResponse(Outcome outcome, String reservationId, Instant expiresAt, boolean replay) {
		static ReservationResponse insufficient() { return new ReservationResponse(Outcome.INSUFFICIENT_BALANCE, null, null, false); }
		static ReservationResponse noPlan() { return new ReservationResponse(Outcome.NO_PLAN, null, null, false); }
		static ReservationResponse unavailable() { return new ReservationResponse(Outcome.NO_PLAN, null, null, false); }
	}

	public record BalanceResponse(long allowanceBytes, long reservedBytes, long consumedBytes, long remainingBytes,
			Instant cycleStart) {
	}
}
