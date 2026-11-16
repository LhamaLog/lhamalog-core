package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.application.MongoPlanSubscriptionRepository;
import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanTier;
import br.com.lhamalog.core.usage.application.PlanType;
import br.com.lhamalog.core.usage.application.SubscriptionStatus;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PlanSubscriptionPlanTierIntegrationTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private MongoTemplate mongo;
	private MongoPlanSubscriptionRepository repository;

	@BeforeEach
	void setUp() {
		var factory = new SimpleMongoClientDatabaseFactory(MongoClients.create(MONGO.getConnectionString()),
				"usage-plan-tier");
		var conversions = UsageMongoConverters.utc();
		var mappingContext = new MongoMappingContext();
		mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		mappingContext.afterPropertiesSet();
		var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
		mongo = new MongoTemplate(factory, converter);
		mongo.dropCollection(PlanSubscription.class);
		repository = new MongoPlanSubscriptionRepository(mongo);
	}

	@AfterEach
	void tearDown() {
		if (mongo != null) {
			mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
		}
	}

	@Test
	void readsLegacyDocumentWithoutPlanTierAsNull() {
		mongo.getCollection("PlanSubscription").insertOne(new Document()
				.append("_id", "sub-legacy")
				.append("tenantId", "tenant-1")
				.append("productId", "product-1")
				.append("planType", PlanType.MONTHLY.name())
				.append("firstPaymentDate", Date.from(Instant.parse("2026-11-01T00:00:00Z")))
				.append("_class", PlanSubscription.class.getName()));

		PlanSubscription subscription = mongo.findById("sub-legacy", PlanSubscription.class);
		assertThat(subscription).isNotNull();
		assertThat(subscription.planTier()).isNull();
		assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
	}

	@Test
	void readsCaseInsensitivePlanTierFromRawDocument() {
		mongo.getCollection("PlanSubscription").insertOne(new Document()
				.append("_id", "sub-starter")
				.append("tenantId", "tenant-1")
				.append("productId", "product-2")
				.append("planType", PlanType.MONTHLY.name())
				.append("firstPaymentDate", Date.from(Instant.parse("2026-11-01T00:00:00Z")))
				.append("allowanceGigabytes", new BigDecimal("0.01"))
				.append("unlimitedUsage", false)
				.append("status", SubscriptionStatus.ACTIVE.name())
				.append("overageEnabled", false)
				.append("planTier", "Starter")
				.append("_class", PlanSubscription.class.getName()));

		PlanSubscription subscription = mongo.findById("sub-starter", PlanSubscription.class);
		assertThat(subscription).isNotNull();
		assertThat(subscription.planTier()).isEqualTo(PlanTier.STARTER);
	}

	@Test
	void findByTenantIdReturnsAllProductsForTenant() {
		repository.save(new PlanSubscription("a", "tenant-1", "product-a", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.ACTIVE, false, null,
				PlanTier.FREE));
		repository.save(new PlanSubscription("b", "tenant-1", "product-b", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.ACTIVE, false, null,
				PlanTier.PRO));
		repository.save(new PlanSubscription("c", "tenant-2", "product-a", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.ACTIVE, false, null,
				PlanTier.BUSINESS));

		List<PlanSubscription> found = repository.findByTenantId("tenant-1");
		assertThat(found).extracting(PlanSubscription::productId).containsExactlyInAnyOrder("product-a", "product-b");
		assertThat(found).extracting(PlanSubscription::planTier)
				.containsExactlyInAnyOrder(PlanTier.FREE, PlanTier.PRO);
	}
}
