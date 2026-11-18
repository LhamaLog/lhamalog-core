package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.application.QuotaReservation;
import br.com.lhamalog.core.usage.application.QuotaReservationRepository;
import br.com.lhamalog.core.usage.application.ReservationState;
import br.com.lhamalog.core.usage.application.LowBalanceNotification;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MongoQuotaReservationRepositoryIntegrationTest {
	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private MongoTemplate mongo;
	private MongoQuotaReservationRepository repository;
	private final Instant cycleStart = Instant.parse("2026-11-01T00:00:00Z");

	@BeforeEach
	void setUp() {
		mongo = new MongoTemplate(MongoClients.create(MONGO.getConnectionString()), "quota-reservations");
		mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
		new UsageMongoIndexInitializer(mongo).ensureIndexes();
		repository = new MongoQuotaReservationRepository(mongo);
	}

	@AfterEach
	void tearDown() {
		if (mongo != null) mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
	}

	@Test
	void concurrentConditionalReservationsNeverOverdraw() throws Exception {
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, 1_000);
		try (var executor = Executors.newFixedThreadPool(16)) {
			List<Callable<Boolean>> requests = java.util.stream.IntStream.range(0, 20)
					.<Callable<Boolean>>mapToObj(index -> () -> repository.reserve(balance, reservation("reserve-" + index, 100,
							ReservationState.RESERVED, Instant.parse("2026-11-11T12:20:00Z"))))
					.toList();
			long authorized = executor.invokeAll(requests).stream().filter(result -> {
				try {
					return result.get();
				}
				catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).count();
			assertThat(authorized).isEqualTo(10);
		}
		assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), QuotaReservationDocument.class))
				.isEqualTo(10);
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isEqualTo(1_000);
			assertThat(value.consumedBytes()).isZero();
			assertThat(value.remainingBytes()).isZero();
			assertThat(value.overageBytes()).isZero();
		});
	}

	@Test
	void concurrentOverageReservationsSplitExactlyAcrossThreads() throws Exception {
		long allowance = 1_000;
		long requested = 100;
		int threads = 50;
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, allowance);
		AtomicLong overageAuthorized = new AtomicLong();
		try (var executor = Executors.newFixedThreadPool(threads)) {
			List<Callable<Long>> requests = java.util.stream.IntStream.range(0, threads)
					.<Callable<Long>>mapToObj(index -> () -> {
						long overage = repository.reserveAllowingOverage(balance,
								reservation("overage-" + index, requested, ReservationState.RESERVED,
										Instant.parse("2026-11-11T12:20:00Z")));
						if (overage >= 0) {
							overageAuthorized.addAndGet(overage);
						}
						return overage;
					})
					.toList();
			List<Future<Long>> futures = executor.invokeAll(requests);
			long authorized = 0;
			for (Future<Long> future : futures) {
				assertThat(future.get()).isGreaterThanOrEqualTo(0);
				authorized++;
			}
			assertThat(authorized).isEqualTo(threads);
		}
		long totalAuthorizedBytes = threads * requested;
		long expectedOverage = totalAuthorizedBytes - allowance;
		assertThat(overageAuthorized.get()).isEqualTo(expectedOverage);
		Instant confirmedAt = Instant.parse("2026-11-11T12:30:00Z");
		for (int index = 0; index < threads; index++) {
			assertThat(repository.moveReservedToConsumed(repository.find("overage-" + index).orElseThrow(), confirmedAt))
					.isTrue();
		}
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isZero();
			assertThat(value.consumedBytes()).isEqualTo(totalAuthorizedBytes);
			assertThat(value.overageBytes()).isEqualTo(expectedOverage);
			assertThat(value.consumedBytes()).isEqualTo(value.allowanceBytes() + value.overageBytes());
		});
	}

	@Test
	void concurrentReservationsWithoutOverageNeverExceedAllowance() throws Exception {
		long allowance = 1_000;
		long requested = 100;
		int threads = 50;
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, allowance);
		try (var executor = Executors.newFixedThreadPool(threads)) {
			List<Callable<Boolean>> requests = java.util.stream.IntStream.range(0, threads)
					.<Callable<Boolean>>mapToObj(index -> () -> repository.reserve(balance,
							reservation("strict-" + index, requested, ReservationState.RESERVED,
									Instant.parse("2026-11-11T12:20:00Z"))))
					.toList();
			long authorized = 0;
			for (Future<Boolean> future : executor.invokeAll(requests)) {
				if (Boolean.TRUE.equals(future.get())) {
					authorized++;
				}
			}
			assertThat(authorized).isEqualTo(allowance / requested);
		}
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isEqualTo(allowance);
			assertThat(value.overageBytes()).isZero();
			assertThat(value.remainingBytes()).isZero();
		});
	}

	@Test
	void overageReleaseAndRefundRevertOverageExactlyOnce() {
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, 100);
		assertThat(repository.reserveAllowingOverage(balance,
				reservation("overage-release", 150, ReservationState.RESERVED, Instant.parse("2026-11-11T12:20:00Z"))))
				.isEqualTo(50L);
		QuotaReservation pending = repository.find("overage-release").orElseThrow();
		assertThat(pending.overageBytes()).isEqualTo(50L);
		assertThat(repository.releaseReserved(pending, ReservationState.RELEASED, Instant.parse("2026-11-11T12:00:00Z")))
				.isTrue();
		assertThat(repository.releaseReserved(pending, ReservationState.RELEASED, Instant.parse("2026-11-11T12:00:01Z")))
				.isFalse();
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isZero();
			assertThat(value.overageBytes()).isZero();
		});

		assertThat(repository.reserveAllowingOverage(balance,
				reservation("overage-refund", 150, ReservationState.RESERVED, Instant.parse("2026-11-11T12:40:00Z"))))
				.isEqualTo(50L);
		QuotaReservation confirmed = repository.find("overage-refund").orElseThrow();
		assertThat(repository.moveReservedToConsumed(confirmed, Instant.parse("2026-11-11T12:00:02Z"))).isTrue();
		assertThat(repository.findBalance("tenant", "product", cycleStart))
				.hasValueSatisfying(value -> assertThat(value.overageBytes()).isEqualTo(50L));
		QuotaReservation toRefund = repository.find("overage-refund").orElseThrow();
		assertThat(repository.refundConsumed(toRefund, Instant.parse("2026-11-11T12:00:03Z"))).isTrue();
		assertThat(repository.refundConsumed(toRefund, Instant.parse("2026-11-11T12:00:04Z"))).isFalse();
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.consumedBytes()).isZero();
			assertThat(value.overageBytes()).isZero();
		});
	}

	@Test
	void releaseAndRefundMutateTheirBalanceAtMostOnce() {
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, 1_000);
		QuotaReservation pending = reservation("expired", 400, ReservationState.RESERVED, Instant.parse("2026-11-11T11:59:00Z"));
		assertThat(repository.reserve(balance, pending)).isTrue();
		assertThat(repository.releaseReserved(pending, ReservationState.EXPIRED, Instant.parse("2026-11-11T12:00:00Z"))).isTrue();
		assertThat(repository.releaseReserved(pending, ReservationState.EXPIRED, Instant.parse("2026-11-11T12:00:01Z"))).isFalse();
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isZero();
			assertThat(value.consumedBytes()).isZero();
		});

		QuotaReservation confirmed = reservation("confirmed", 300, ReservationState.RESERVED, Instant.parse("2026-11-11T12:20:00Z"));
		assertThat(repository.reserve(balance, confirmed)).isTrue();
		assertThat(repository.moveReservedToConsumed(confirmed, Instant.parse("2026-11-11T12:00:02Z"))).isTrue();
		assertThat(repository.refundConsumed(repository.find("confirmed").orElseThrow(), Instant.parse("2026-11-11T12:00:03Z"))).isTrue();
		assertThat(repository.refundConsumed(repository.find("confirmed").orElseThrow(), Instant.parse("2026-11-11T12:00:04Z"))).isFalse();
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isZero();
			assertThat(value.consumedBytes()).isZero();
		});
	}

	@Test
	void reserveReplacesTerminalReservationWithSameId() {
		var balance = new QuotaReservationRepository.CycleBalance("tenant", "product", cycleStart, 1_000);
		QuotaReservation first = reservation("reuse", 100, ReservationState.RESERVED, Instant.parse("2026-11-11T12:20:00Z"));
		assertThat(repository.reserve(balance, first)).isTrue();
		assertThat(repository.releaseReserved(first, ReservationState.RELEASED, Instant.parse("2026-11-11T12:01:00Z"))).isTrue();

		QuotaReservation retry = reservation("reuse", 150, ReservationState.RESERVED, Instant.parse("2026-11-11T12:40:00Z"));
		assertThat(repository.reserve(balance, retry)).isTrue();
		assertThat(repository.find("reuse")).hasValueSatisfying(value -> {
			assertThat(value.state()).isEqualTo(ReservationState.RESERVED);
			assertThat(value.requestedBytes()).isEqualTo(150);
			assertThat(value.expiresAt()).isEqualTo(Instant.parse("2026-11-11T12:40:00Z"));
			assertThat(value.overageBytes()).isZero();
		});
		assertThat(repository.findBalance("tenant", "product", cycleStart)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isEqualTo(150);
			assertThat(value.consumedBytes()).isZero();
		});
	}

	@Test
	void notificationOutboxUsesTheCycleThresholdAsItsDurableUniqueKey() {
		MongoNotificationOutboxRepository outbox = new MongoNotificationOutboxRepository(mongo);
		LowBalanceNotification first = LowBalanceNotification.pending("tenant", "product", cycleStart, 20, 2_000,
				Instant.parse("2026-11-11T12:00:00Z"));
		LowBalanceNotification duplicate = LowBalanceNotification.pending("tenant", "product", cycleStart, 20, 1_500,
				Instant.parse("2026-11-11T12:01:00Z"));

		assertThat(outbox.saveIfAbsent(first)).isTrue();
		assertThat(outbox.saveIfAbsent(duplicate)).isFalse();
		assertThat(outbox.saveIfAbsent(LowBalanceNotification.pending("tenant", "product", cycleStart, 5, 500,
				Instant.parse("2026-11-11T12:02:00Z")))).isTrue();
		assertThat(outbox.saveIfAbsent(LowBalanceNotification.pending("tenant", "other-product", cycleStart, 20, 2_000,
				Instant.parse("2026-11-11T12:03:00Z")))).isTrue();
		assertThat(outbox.findPending()).hasSize(3).extracting(LowBalanceNotification::idempotencyKey)
				.containsExactlyInAnyOrder(first.idempotencyKey(),
						"tenant+product+2026-11-01T00:00:00Z+5",
						"tenant+other-product+2026-11-01T00:00:00Z+20");
	}

	private QuotaReservation reservation(String id, long requestedBytes, ReservationState state, Instant expiresAt) {
		Instant createdAt = Instant.parse("2026-11-11T11:00:00Z");
		return new QuotaReservation(id, "tenant", "product", cycleStart, requestedBytes, UsageDomain.LOGS, "batch", false,
				state, createdAt, expiresAt, createdAt);
	}
}
