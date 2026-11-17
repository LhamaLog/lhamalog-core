package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanType;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.TimeZone;
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
class PlanSubscriptionLocalDateUtcIntegrationTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.34"));

	private TimeZone previous;
	private MongoTemplate mongo;

	@BeforeEach
	void setUp() {
		previous = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
		var factory = new SimpleMongoClientDatabaseFactory(MongoClients.create(MONGO.getConnectionString()),
				"usage-localdate-utc");
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
		if (previous != null) {
			TimeZone.setDefault(previous);
		}
	}

	@Test
	void readsUtcMidnightSeedAsTheCivilUtcDateEvenInAmericaSaoPaulo() {
		mongo.getCollection("PlanSubscription").insertOne(new Document()
				.append("_id", "sub-utc")
				.append("tenantId", "tenant-e2e-a")
				.append("productId", "product-e2e-a")
				.append("planType", PlanType.MONTHLY.name())
				.append("firstPaymentDate", Date.from(Instant.parse("2026-11-01T00:00:00Z")))
				.append("_class", PlanSubscription.class.getName()));

		PlanSubscription subscription = mongo.findById("sub-utc", PlanSubscription.class);
		assertThat(subscription).isNotNull();
		assertThat(subscription.firstPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 1));
	}
}
