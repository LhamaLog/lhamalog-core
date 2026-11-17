package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanType;
import br.com.lhamalog.core.usage.application.SubscriptionStatus;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
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
class SubscriptionStatusMongoCompatibilityTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private MongoTemplate mongo;

	@BeforeEach
	void setUp() {
		var factory = new SimpleMongoClientDatabaseFactory(MongoClients.create(MONGO.getConnectionString()),
				"subscription-status-compat");
		var conversions = UsageMongoConverters.utc();
		var mappingContext = new MongoMappingContext();
		mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		mappingContext.afterPropertiesSet();
		var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
		mongo = new MongoTemplate(factory, converter);
		mongo.dropCollection(PlanSubscription.class);
	}

	@AfterEach
	void tearDown() {
		if (mongo != null) {
			mongo.getMongoDatabaseFactory().getMongoDatabase().drop();
		}
	}

	@Test
	void readsLegacyPortugueseStatusValuesAsEnglishEnum() {
		insertRaw("sub-ativa", "ATIVA");
		insertRaw("sub-suspensa", "SUSPENSA");
		insertRaw("sub-inadimplente", "INADIMPLENTE");

		assertThat(mongo.findById("sub-ativa", PlanSubscription.class).status())
				.isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(mongo.findById("sub-suspensa", PlanSubscription.class).status())
				.isEqualTo(SubscriptionStatus.SUSPENDED);
		assertThat(mongo.findById("sub-inadimplente", PlanSubscription.class).status())
				.isEqualTo(SubscriptionStatus.DELINQUENT);
	}

	@Test
	void writesEnglishStatusValues() {
		PlanSubscription subscription = new PlanSubscription("sub-write", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.SUSPENDED, true,
				Instant.parse("2026-11-01T00:00:00Z"));
		mongo.insert(subscription);

		Document raw = mongo.getCollection("PlanSubscription").find(new Document("_id", "sub-write")).first();
		assertThat(raw).isNotNull();
		assertThat(raw.getString("status")).isEqualTo("SUSPENDED");
	}

	private void insertRaw(String id, String status) {
		mongo.getCollection("PlanSubscription").insertOne(new Document()
				.append("_id", id)
				.append("tenantId", "tenant-" + id)
				.append("productId", "product")
				.append("planType", PlanType.MONTHLY.name())
				.append("firstPaymentDate", Date.from(Instant.parse("2026-11-01T00:00:00Z")))
				.append("allowanceGigabytes", 0.01)
				.append("unlimitedUsage", false)
				.append("status", status)
				.append("overageEnabled", false)
				.append("_class", PlanSubscription.class.getName()));
	}
}
