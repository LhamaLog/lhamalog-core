package br.com.lhamalog.core.webhook.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import br.com.lhamalog.core.support.CoreIntegrationTest;

@CoreIntegrationTest
class MongoProcessedWebhookRepositoryTests {

	@Autowired
	private MongoProcessedWebhookRepository repository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void setUp() {
		mongoTemplate.remove(new Query(), ProcessedWebhookDocument.class);
	}

	@Test
	void recordsAndFindsProcessedMessage() {
		assertThat(repository.isProcessed("msg_1")).isFalse();

		repository.markProcessed("msg_1", "user.updated", null, Instant.now());

		assertThat(repository.isProcessed("msg_1")).isTrue();
	}

	@Test
	void eventWithoutTenantStoresAnExplicitNullTenantId() {
		repository.markProcessed("msg_3", "user.updated", null, Instant.now());

		Document stored = mongoTemplate.getCollection(ProcessedWebhookDocument.COLLECTION)
			.find(new Document("_id", "msg_3"))
			.first();
		assertThat(stored).isNotNull().containsEntry("tenantId", null);
	}

	@Test
	void recordingTwiceIsNotAnError() {
		repository.markProcessed("msg_2", "user.updated", null, Instant.now());
		repository.markProcessed("msg_2", "user.updated", null, Instant.now());

		assertThat(mongoTemplate.count(new Query(), ProcessedWebhookDocument.class)).isEqualTo(1);
	}

	@Test
	void processedMessagesExpireAfterSevenDays() {
		Document ttl = mongoTemplate.getCollection(ProcessedWebhookDocument.COLLECTION)
			.listIndexes()
			.into(new java.util.ArrayList<>())
			.stream()
			.filter((index) -> MongoProcessedWebhookRepository.TTL_INDEX.equals(index.getString("name")))
			.findFirst()
			.orElseThrow();

		assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isEqualTo(7 * 24 * 3600L);
	}

}
