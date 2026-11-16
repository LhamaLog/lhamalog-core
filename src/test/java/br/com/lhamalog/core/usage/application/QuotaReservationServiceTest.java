package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
	void confirmSucceedsWhenSaldoBaixoPublishFailsAfterDebit() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(100);
		List<LowBalanceNotification> pending = new ArrayList<>();
		LowBalanceNotificationService notifications = new LowBalanceNotificationService(
				new NotificationOutboxRepository() {
					@Override public boolean saveIfAbsent(LowBalanceNotification notification) {
						pending.add(notification);
						return true;
					}
					@Override public List<LowBalanceNotification> findPending() {
						return List.copyOf(pending);
					}
					@Override public boolean markPublished(String idempotencyKey, Instant publishedAt) {
						return false;
					}
				},
				Optional.of(notification -> {
					throw new IllegalStateException("notification stream unavailable");
				}));
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.0000001), false)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20), notifications);

		assertThat(service.reserve(request("near-limit")).outcome()).isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
		assertThat(service.confirm("near-limit")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.find("near-limit").orElseThrow().state()).isEqualTo(ReservationState.CONFIRMED);
		assertThat(pending).isNotEmpty();
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

	@Test
	void suspendedSubscriptionReturnsSuspendedWithoutCreatingReservation() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false, SubscriptionStatus.SUSPENSA,
						false)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20));

		QuotaReservationService.ReservationResponse response = service.reserve(request("suspended"));
		assertThat(response.outcome()).isEqualTo(QuotaReservationService.Outcome.SUSPENDED);
		assertThat(response.reservationId()).isNull();
		assertThat(response.expiresAt()).isNull();
		assertThat(store.find("suspended")).isEmpty();
	}

	@Test
	void suspendedTakesPrecedenceOverMissingAllowance() {
		QuotaReservationService service = service(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.ZERO, false, SubscriptionStatus.SUSPENSA, false));
		assertThat(service.reserve(request("suspended-no-allowance")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.SUSPENDED);
	}

	@Test
	void overageEnabledAuthorizesBeyondBalanceAndRecordsOverageBytes() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(50);
		SimpleMeterRegistry meters = new SimpleMeterRegistry();
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false, SubscriptionStatus.ATIVA, true)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20), Duration.ofDays(15), null, meters);

		QuotaReservationService.ReservationResponse response = service.reserve(request("overage"));
		// AUTHORIZED + overageBytes keeps current Collector HttpQuotaReservationClient compatible (valueOf).
		assertThat(response.outcome()).isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
		assertThat(response.reservationId()).isEqualTo("overage");
		assertThat(response.overageBytes()).isEqualTo(50L);
		assertThat(store.find("overage")).isPresent();
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> assertThat(balance.overageBytes()).isEqualTo(50L));
		assertThat(meters.counter("lhamalog.quota.overage", "productId", "product").count()).isEqualTo(1.0);
		assertThat(meters.summary("lhamalog.quota.overage.bytes", "productId", "product").totalAmount())
				.isEqualTo(50.0);
	}

	@Test
	void overageDisabledStillRejectsInsufficientBalance() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(50);
		QuotaReservationService service = new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false, SubscriptionStatus.ATIVA,
						false)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20));

		assertThat(service.reserve(request("too-big")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.INSUFFICIENT_BALANCE);
		assertThat(store.find("too-big")).isEmpty();
	}

	@Test
	void overageReleaseRefundAndExpirationRevertOverageExactlyOnce() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(50);
		QuotaReservationService service = overageService(store);

		assertThat(service.reserve(request("release-overage")).overageBytes()).isEqualTo(50L);
		assertThat(store.find("release-overage")).hasValueSatisfying(value -> assertThat(value.overageBytes()).isEqualTo(50L));
		assertThat(service.release("release-overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.release("release-overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.reservedBytes()).isZero();
					assertThat(balance.overageBytes()).isZero();
				});

		assertThat(service.reserve(request("refund-overage")).overageBytes()).isEqualTo(50L);
		assertThat(service.confirm("refund-overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> assertThat(balance.overageBytes()).isEqualTo(50L));
		assertThat(service.refund("refund-overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(service.refund("refund-overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.consumedBytes()).isZero();
					assertThat(balance.overageBytes()).isZero();
				});

		store.seedExpired("expire-overage", 100L, 50L);
		assertThat(service.expireReservations()).isEqualTo(1);
		assertThat(service.expireReservations()).isZero();
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.reservedBytes()).isZero();
					assertThat(balance.overageBytes()).isZero();
				});
	}

	@Test
	void mixedNormalAndOverageReservationsRevertOnlyTheirOverageShare() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(100);
		QuotaReservationService service = overageService(store);

		assertThat(service.reserve(new QuotaReservationService.ReservationRequest("normal", "tenant", "product", 60L,
				"batch", UsageDomain.LOGS)).overageBytes()).isZero();
		assertThat(service.reserve(new QuotaReservationService.ReservationRequest("overage", "tenant", "product", 80L,
				"batch", UsageDomain.LOGS)).overageBytes()).isEqualTo(40L);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.reservedBytes()).isEqualTo(140L);
					assertThat(balance.overageBytes()).isEqualTo(40L);
				});

		assertThat(service.release("normal")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.reservedBytes()).isEqualTo(80L);
					assertThat(balance.overageBytes()).isEqualTo(40L);
				});

		assertThat(service.release("overage")).isEqualTo(QuotaReservationService.TransitionResult.OK);
		assertThat(store.findBalance("tenant", "product", Instant.parse("2026-11-01T00:00:00Z")))
				.hasValueSatisfying(balance -> {
					assertThat(balance.reservedBytes()).isZero();
					assertThat(balance.overageBytes()).isZero();
				});
	}

	@Test
	void inadimplenteWithinGraceIsAuthorized() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		Instant delinquentSince = NOW.minus(Duration.ofDays(5));
		QuotaReservationService service = delinquentService(store, delinquentSince, Duration.ofDays(15));

		assertThat(service.reserve(request("in-grace")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
		assertThat(store.find("in-grace")).isPresent();
	}

	@Test
	void inadimplenteExactlyAtGraceBoundaryIsSuspended() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		Instant delinquentSince = NOW.minus(Duration.ofDays(15));
		QuotaReservationService service = delinquentService(store, delinquentSince, Duration.ofDays(15));

		QuotaReservationService.ReservationResponse response = service.reserve(request("at-boundary"));
		assertThat(response.outcome()).isEqualTo(QuotaReservationService.Outcome.SUSPENDED);
		assertThat(response.reservationId()).isNull();
		assertThat(store.find("at-boundary")).isEmpty();
	}

	@Test
	void inadimplenteAfterGraceIsSuspendedWithoutReservation() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		Instant delinquentSince = NOW.minus(Duration.ofDays(16));
		QuotaReservationService service = delinquentService(store, delinquentSince, Duration.ofDays(15));

		QuotaReservationService.ReservationResponse response = service.reserve(request("after-grace"));
		assertThat(response.outcome()).isEqualTo(QuotaReservationService.Outcome.SUSPENDED);
		assertThat(store.find("after-grace")).isEmpty();
	}

	@Test
	void inadimplenteWithNullDelinquentSinceIsAllowedConservatively() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		QuotaReservationService service = delinquentService(store, null, Duration.ofDays(15));

		assertThat(service.reserve(request("missing-since")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);
		assertThat(store.find("missing-since")).isPresent();
	}

	@Test
	void customDelinquencyGraceIsHonored() {
		MemoryReservations store = new MemoryReservations();
		store.seedBalance(10_000);
		Instant delinquentSince = NOW.minus(Duration.ofDays(3));
		QuotaReservationService within = delinquentService(store, delinquentSince, Duration.ofDays(7));
		assertThat(within.reserve(request("custom-within")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.AUTHORIZED);

		MemoryReservations afterStore = new MemoryReservations();
		afterStore.seedBalance(10_000);
		QuotaReservationService expired = delinquentService(afterStore, delinquentSince, Duration.ofDays(2));
		assertThat(expired.reserve(request("custom-expired")).outcome())
				.isEqualTo(QuotaReservationService.Outcome.SUSPENDED);
		assertThat(afterStore.find("custom-expired")).isEmpty();
	}

	private static QuotaReservationService delinquentService(MemoryReservations store, Instant delinquentSince,
			Duration grace) {
		return new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false, SubscriptionStatus.INADIMPLENTE,
						false, delinquentSince)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20), grace);
	}

	private static QuotaReservationService overageService(MemoryReservations store) {
		return new QuotaReservationService(
				(tenant, product) -> Optional.of(new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
						LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.00001), false, SubscriptionStatus.ATIVA, true)),
				store, InstantSource.fixed(NOW), Duration.ofMinutes(20));
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
		private long overage;

		void seedBalance(long allowanceBytes) {
			this.allowance = allowanceBytes;
			this.remaining.set(allowanceBytes);
			this.reserved = 0;
			this.consumed = 0;
			this.overage = 0;
			this.values.clear();
		}

		void seedExpired(String id, long requestedBytes, long overageBytes) {
			Instant createdAt = NOW.minus(Duration.ofMinutes(30));
			QuotaReservation expired = new QuotaReservation(id, "tenant", "product", cycleStart, requestedBytes,
					UsageDomain.LOGS, "batch", false, ReservationState.RESERVED, createdAt, NOW.minusSeconds(1),
					createdAt, overageBytes);
			values.put(id, expired);
			reserved += requestedBytes;
			overage += overageBytes;
			remaining.addAndGet(-requestedBytes);
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
				if (!persist(reservation.withOverageBytes(0L))) {
					return false;
				}
				remaining.addAndGet(-reservation.requestedBytes());
				reserved += reservation.requestedBytes();
				return true;
			}
		}

		@Override
		public boolean saveUnlimited(QuotaReservation reservation) {
			return persist(reservation.withOverageBytes(0L));
		}

		@Override
		public long reserveAllowingOverage(CycleBalance balance, QuotaReservation reservation) {
			synchronized (this) {
				long shortfall = Math.max(0, reservation.requestedBytes() - Math.max(0, remaining.get()));
				if (!persist(reservation.withOverageBytes(shortfall))) {
					return -1L;
				}
				remaining.addAndGet(-reservation.requestedBytes());
				reserved += reservation.requestedBytes();
				overage += shortfall;
				return shortfall;
			}
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
			QuotaReservation current = values.get(value.reservationId());
			if (current == null || current.state() != ReservationState.RESERVED) {
				return false;
			}
			if (!transition(value.reservationId(), ReservationState.RESERVED, next, now)) {
				return false;
			}
			if (!value.unlimited()) {
				synchronized (this) {
					reserved -= current.requestedBytes();
					remaining.addAndGet(current.requestedBytes());
					overage -= current.overageBytes();
				}
			}
			return true;
		}

		@Override
		public boolean refundConsumed(QuotaReservation value, Instant now) {
			QuotaReservation current = values.get(value.reservationId());
			if (current == null || current.state() != ReservationState.CONFIRMED) {
				return false;
			}
			if (!transition(value.reservationId(), ReservationState.CONFIRMED, ReservationState.REFUNDED, now)) {
				return false;
			}
			if (!value.unlimited()) {
				synchronized (this) {
					consumed -= current.requestedBytes();
					remaining.addAndGet(current.requestedBytes());
					overage -= current.overageBytes();
				}
			}
			return true;
		}

		@Override
		public List<QuotaReservation> findExpiredReserved(Instant now) {
			return values.values().stream()
					.filter(value -> value.state() == ReservationState.RESERVED && value.expiresAt().isBefore(now))
					.toList();
		}

		@Override
		public Optional<CycleBalanceSnapshot> findBalance(String tenantId, String productId, Instant cycleStart) {
			if (allowance == Long.MAX_VALUE) {
				return Optional.empty();
			}
			return Optional.of(new CycleBalanceSnapshot(allowance, reserved, consumed, this.cycleStart, overage));
		}
	}
}
