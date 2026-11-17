package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanType;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MongoUsageFactRepositoryIntegrationTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private MongoTemplate mongo;
	private MongoUsageFactRepository repository;

	@BeforeEach
	void setUp() {
		mongo = new MongoTemplate(MongoClients.create(MONGO.getConnectionString()), "usage-review");
		mongo.dropCollection(UsageFactDocument.class);
		mongo.dropCollection(UsageFactIdempotencyDocument.class);
		mongo.dropCollection(PlanSubscription.class);
		new UsageMongoIndexInitializer(mongo).ensureIndexes();
		repository = new MongoUsageFactRepository(mongo);
	}

	@AfterEach
	void tearDown() {
		if (mongo != null) {
			mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
		}
	}

	@Test
	void persistsAcceptedAtAsInstantAndFiltersTheHalfOpenWindow() {
		OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-11-09T12:00:00Z");
		UsageFactRepository.StoredUsageFact stored = stored("fact-window", "batch-window", UsageDomain.LOGS,
				acceptedAt, 128);
		assertThat(repository.saveIfAbsent(stored)).isEqualTo(UsageFactRepository.SaveResult.CREATED);

		UsageFactDocument document = mongo.findOne(new Query(), UsageFactDocument.class);
		assertThat(document).isNotNull();
		assertThat(document.acceptedAt()).isEqualTo(acceptedAt.toInstant());
		assertThat(document.acceptedAt()).isInstanceOf(Instant.class);

		Instant from = Instant.parse("2026-11-09T12:00:00Z");
		Instant to = Instant.parse("2026-11-09T13:00:00Z");
		assertThat(repository.find("tenant-1", "product-1", from, to)).hasSize(1);
		assertThat(repository.find("tenant-1", "product-1", Instant.parse("2026-11-09T12:00:01Z"), to)).isEmpty();
		assertThat(repository.find("tenant-1", "product-1", Instant.parse("2026-11-09T11:00:00Z"), from)).isEmpty();
	}

	@Test
	void treatsIdenticalResendAsDuplicateAndConflictingBodyAsConflict() {
		UsageFactRepository.StoredUsageFact first = stored("fact-dup", "batch-dup", UsageDomain.LOGS,
				OffsetDateTime.parse("2026-11-09T12:00:00Z"), 10);
		assertThat(repository.saveIfAbsent(first)).isEqualTo(UsageFactRepository.SaveResult.CREATED);
		assertThat(repository.saveIfAbsent(first)).isEqualTo(UsageFactRepository.SaveResult.DUPLICATE);
		assertThat(repository.saveIfAbsent(stored("fact-dup", "batch-dup", UsageDomain.LOGS,
				OffsetDateTime.parse("2026-11-09T12:00:00Z"), 99)))
				.isEqualTo(UsageFactRepository.SaveResult.CONFLICT);
		assertThat(mongo.findAll(UsageFactDocument.class)).hasSize(1);
	}

	@Test
	void reinsertsAfterIdempotencyDocumentExpiresInsteadOfReturningConflict() {
		UsageFactRepository.StoredUsageFact first = stored("fact-ttl", "batch-ttl", UsageDomain.TRACES,
				OffsetDateTime.parse("2026-11-09T12:00:00Z"), 20);
		assertThat(repository.saveIfAbsent(first)).isEqualTo(UsageFactRepository.SaveResult.CREATED);
		mongo.remove(new Query(), UsageFactIdempotencyDocument.class);
		assertThat(repository.saveIfAbsent(first)).isEqualTo(UsageFactRepository.SaveResult.CREATED);
		assertThat(mongo.findAll(UsageFactDocument.class)).hasSize(2);
	}

	@Test
	void pagesResultsAndExposesACursor() {
		repository.saveIfAbsent(stored("fact-a", "batch-a", UsageDomain.LOGS,
				OffsetDateTime.parse("2026-11-09T10:00:00Z"), 1));
		repository.saveIfAbsent(stored("fact-b", "batch-b", UsageDomain.LOGS,
				OffsetDateTime.parse("2026-11-09T11:00:00Z"), 2));
		repository.saveIfAbsent(stored("fact-c", "batch-c", UsageDomain.LOGS,
				OffsetDateTime.parse("2026-11-09T12:00:00Z"), 3));

		UsageFactRepository.Page firstPage = repository.findPage("tenant-1", "product-1",
				Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"), 2, null);
		assertThat(firstPage.items()).hasSize(2);
		assertThat(firstPage.nextCursor()).isNotBlank();

		UsageFactRepository.Page secondPage = repository.findPage("tenant-1", "product-1",
				Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"), 2,
				firstPage.nextCursor());
		assertThat(secondPage.items()).hasSize(1);
		assertThat(secondPage.items().get(0).request().usageFactId()).isEqualTo("fact-c");
		assertThat(secondPage.nextCursor()).isNull();
	}

	@Test
	void createsTtlAndUniqueIndexesExplicitlyWithoutExpiringAggregatedFacts() {
		List<Document> idempotencyIndexes = mongo.getCollection("UsageFactIdempotency").listIndexes()
				.into(new ArrayList<>());
		Document ttl = idempotencyIndexes.stream()
				.filter(index -> "usage_fact_idempotency_ttl".equals(index.getString("name")))
				.findFirst().orElseThrow();
		assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isEqualTo(7 * 24 * 60 * 60);

		List<Document> factIndexes = mongo.getCollection("UsageFact").listIndexes().into(new ArrayList<>());
		assertThat(factIndexes.stream().anyMatch(index -> index.containsKey("expireAfterSeconds"))).isFalse();

		List<Document> planIndexes = mongo.getCollection("PlanSubscription").listIndexes().into(new ArrayList<>());
		Document unique = planIndexes.stream()
				.filter(index -> "plan_subscription_tenant_product".equals(index.getString("name")))
				.findFirst().orElseThrow();
		assertThat(unique.get("unique")).isEqualTo(true);

		mongo.insert(new PlanSubscription("sub-1", "tenant-1", "product-1", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1)));
		assertThatThrownBy(() -> mongo.insert(new PlanSubscription("sub-2", "tenant-1", "product-1",
				PlanType.WEEKLY, LocalDate.of(2026, 11, 1)))).isInstanceOf(Exception.class);
	}

	private static UsageFactRepository.StoredUsageFact stored(String usageFactId, String batchId, UsageDomain domain,
			OffsetDateTime acceptedAt, long bytes) {
		UsageFactRequest request = new UsageFactRequest(usageFactId, batchId, "tenant-1", "product-1", domain,
				List.of(usageFactId + "-record"), 1, bytes, acceptedAt.withOffsetSameInstant(ZoneOffset.UTC), 1);
		return new UsageFactRepository.StoredUsageFact(request, Instant.parse("2026-11-09T12:30:00Z"));
	}
}

