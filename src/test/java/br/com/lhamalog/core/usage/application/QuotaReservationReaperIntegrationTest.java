package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.infrastructure.MongoQuotaReservationRepository;
import br.com.lhamalog.core.usage.infrastructure.UsageMongoIndexInitializer;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class QuotaReservationReaperIntegrationTest {
	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
	private static final Instant CYCLE_START = Instant.parse("2026-11-01T00:00:00Z");
	private MongoTemplate mongo;
	private MongoQuotaReservationRepository repository;

	@BeforeEach
	void setUp() {
		mongo = new MongoTemplate(MongoClients.create(MONGO.getConnectionString()), "quota-reaper");
		mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
		new UsageMongoIndexInitializer(mongo).ensureIndexes();
		repository = new MongoQuotaReservationRepository(mongo);
	}

	@AfterEach
	void tearDown() {
		if (mongo != null) mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
	}

	@Test
	void reaperReturnsAnExpiredReservationExactlyOnce() {
		QuotaReservation reservation = new QuotaReservation("expired", "tenant", "product", CYCLE_START, 200,
				UsageDomain.LOGS, "batch", false, ReservationState.RESERVED, NOW.minus(Duration.ofMinutes(30)),
				NOW.minusSeconds(1), NOW.minus(Duration.ofMinutes(30)));
		assertThat(repository.reserve(new QuotaReservationRepository.CycleBalance("tenant", "product", CYCLE_START, 1_000),
				reservation)).isTrue();
		QuotaReservationService service = new QuotaReservationService((tenant, product) -> Optional.empty(), repository,
				InstantSource.fixed(NOW), Duration.ofMinutes(20));
		QuotaReservationReaper reaper = new QuotaReservationReaper(service);

		reaper.reapExpiredReservations();
		reaper.reapExpiredReservations();

		assertThat(repository.find("expired")).hasValueSatisfying(value -> assertThat(value.state()).isEqualTo(ReservationState.EXPIRED));
		assertThat(repository.findBalance("tenant", "product", CYCLE_START)).hasValueSatisfying(value -> {
			assertThat(value.reservedBytes()).isZero();
			assertThat(value.consumedBytes()).isZero();
			assertThat(value.remainingBytes()).isEqualTo(1_000);
		});
	}
}
