package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class QuotaReservationServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

	@Test
	void rejectsSubscriptionWithoutConfiguredAllowance() {
		QuotaReservationService service = service(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.ZERO, false));

		assertThat(service.reserve(request("reservation")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.NO_PLAN);
	}

	@Test
	void rejectsMissingSubscriptionAndDateBeforeFirstPayment() {
		QuotaReservationService missing = new QuotaReservationService((tenant, product) -> Optional.empty(),
				new MemoryReservations(), InstantSource.fixed(NOW), Duration.ofMinutes(20));
		assertThat(missing.reserve(request("missing")).outcome()).isEqualTo(QuotaReservationService.Outcome.NO_PLAN);

		QuotaReservationService beforePayment = service(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 12, 1), BigDecimal.valueOf(0.00001), false));
		assertThat(beforePayment.reserve(request("early")).outcome()).isEqualTo(QuotaReservationService.Outcome.NO_PLAN);
	}

	@Test
	void limitedReservationReplaysWhileActiveAndReattemptsAfterTerminalStates() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20));

		assertThat(service.reserve(request("reservation")).outcome()).isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
		assertThat(service.reserve(request("reservation")).replay()).isTrue();
		assertThat(service.release("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.release("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.reserve(request("reservation")).replay()).isFalse();
		assertThat(service.confirm("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.confirm("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.refund("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.refund("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.reserve(request("reservation")).outcome()).isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
	}

	@Test
	void insufficientBalanceLeavesNoReservation() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(50);
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20));

		assertThat(service.reserve(request("too-big")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.INSUFFICIENT_BALANCE);
		assertThat(store.find("too-big")).isEmpty();
	}

	@Test
	void unlimitedReservationConfirmsAndRefundsIdempotently() {
		QuotaReservationService service = service(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.ZERO, true));

		assertThat(service.reserve(request("reservation")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.AUTHORIZED_UNLIMITED);
		assertThat(service.confirm("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.refund("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.refund("reservation")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.reserve(request("reservation")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.AUTHORIZED_UNLIMITED);
	}

	@Test
	void balanceIsEmptyForUnlimitedAndMissingPlan() {
		QuotaReservationService unlimited = service(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.ZERO, true));
		assertThat(unlimited.balance("tenant", "product")).isEmpty();

		QuotaReservationService missing = new QuotaReservationService((tenant, product) -> Optional.empty(),
				new MemoryReservations(), InstantSource.fixed(NOW), Duration.ofMinutes(20));
		assertThat(missing.balance("tenant", "product")).isEmpty();
	}

	private static QuotaReservationService service(PlanSubscription subscription) {
		return new QuotaReservationService((tenant, product) -> Optional.of(subscription), new MemoryReservations(),
				InstantSource.fixed(NOW), Duration.ofMinutes(20));
	}

	private static QuotaReservationService.ReservationRequest request(String id) {
		return new QuotaReservationService.ReservationRequest(id, "tenant", "product", 100L, "batch", UsageDomain.LOGS);
	}

	private static final class MemoryReservations implements QuotaReservationRepository {
		private static final EnumSet<ReservationState> TERMINAL = EnumSet.of(ReservationState.RELEASED,
				ReservationState.EXPIRED, ReservationState.REFUNDED);
		private final Map<String, QuotaReservation> values = new ConcurrentHashMap<>();
		private final AtomicLong remaining = new AtomicLong(Long.MAX_VALUE);
		private final Instant cycleStart = Instant.parse("2026-11-01T00:00:00Z");
		private long allowance = Long.MAX_VALUE;
		private long reserved;
		private long consumed;

		void seedBalance(long allowanceBytes) {
			this.allowance = allowanceBytes;
			this.remaining.set(allowanceBytes);
			this.reserved = 0;
			this.consumed = 0;
		}

		@Override
		public Optional<QuotaReservation> find(String id) {
			return Optional.ofNullable(values.get(id));
		}

		@Override
		public boolean reserve(CycleBalance balance, QuotaReservation reservation) {
			synchronized (this) {
				if (remaining.get() < reservation.requestedBytes()) {
					return false;
				}
				if (!persist(reservation)) {
					return false;
				}
				remaining.addAndGet(-reservation.requestedBytes());
				reserved += reservation.requestedBytes();
				return true;
			}
		}

		@Override
		public boolean saveUnlimited(QuotaReservation reservation) {
			return persist(reservation);
		}

		private boolean persist(QuotaReservation reservation) {
			QuotaReservation previous = values.get(reservation.reservationId());
			if (previous == null) {
				values.put(reservation.reservationId(), reservation);
				return true;
			}
			if (TERMINAL.contains(previous.state())) {
				values.put(reservation.reservationId(), reservation);
				return true;
			}
			return false;
		}

		@Override
		public boolean transition(String id, ReservationState expected, ReservationState next, Instant now) {
			return values.computeIfPresent(id, (key, value) -> value.state() == expected ? value.withState(next, now) : value)
					.state() == next;
		}

		@Override
		public boolean moveReservedToConsumed(QuotaReservation value, Instant now) {
			if (!transition(value.reservationId(), ReservationState.RESERVED, ReservationState.CONFIRMED, now)) {
				return false;
			}
			if (!value.unlimited()) {
				synchronized (this) {
					reserved -= value.requestedBytes();
					consumed += value.requestedBytes();
				}
			}
			return true;
		}

		@Override
		public boolean releaseReserved(QuotaReservation value, ReservationState next, Instant now) {
			if (!transition(value.reservationId(), ReservationState.RESERVED, next, now)) {
				return false;
			}
			if (!value.unlimited()) {
				synchronized (this) {
					reserved -= value.requestedBytes();
					remaining.addAndGet(value.requestedBytes());
				}
			}
			return true;
		}

		@Override
		public boolean refundConsumed(QuotaReservation value, Instant now) {
			if (!transition(value.reservationId(), ReservationState.CONFIRMED, ReservationState.REFUNDED, now)) {
				return false;
			}
			if (!value.unlimited()) {
				synchronized (this) {
					consumed -= value.requestedBytes();
					remaining.addAndGet(value.requestedBytes());
				}
			}
			return true;
		}

		@Override
		public List<QuotaReservation> findExpiredReserved(Instant now) {
			return new ArrayList<>();
		}

		@Override
		public Optional<CycleBalanceSnapshot> findBalance(String tenantId, String productId, Instant cycleStart) {
			if (allowance == Long.MAX_VALUE) {
				return Optional.empty();
			}
			return Optional.of(new CycleBalanceSnapshot(allowance, reserved, consumed, this.cycleStart));
		}
	}
}
