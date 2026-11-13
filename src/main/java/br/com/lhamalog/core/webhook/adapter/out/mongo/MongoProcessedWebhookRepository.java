package br.com.lhamalog.core.webhook.adapter.out.mongo;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.webhook.application.port.out.ProcessedWebhookRepository;

/**
 * MongoDB adapter of {@link ProcessedWebhookRepository}. The svix-id is the {@code _id} (unique) and records expire
 * after {@link #RETENTION}, which covers the Svix retry window (about a day) plus manual replays.
 */
@Repository
public class MongoProcessedWebhookRepository implements ProcessedWebhookRepository, InitializingBean {

	static final Duration RETENTION = Duration.ofDays(7);

	static final String TTL_INDEX = "clerk_webhook_event_ttl";

	private final MongoTemplate mongoTemplate;

	public MongoProcessedWebhookRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void afterPropertiesSet() {
		mongoTemplate.indexOps(ProcessedWebhookDocument.class)
			.createIndex(new Index().on(ProcessedWebhookDocument.PROCESSED_AT, Sort.Direction.ASC)
				.expire(RETENTION)
				.named(TTL_INDEX));
	}

	@Override
	public boolean isProcessed(String svixId) {
		return mongoTemplate.exists(Query.query(Criteria.where("_id").is(svixId)), ProcessedWebhookDocument.class);
	}

	@Override
	public void markProcessed(String svixId, String type, String tenantId, Instant processedAt) {
		try {
			mongoTemplate.insert(new ProcessedWebhookDocument(svixId, type, tenantId, processedAt));
		}
		catch (DuplicateKeyException ex) {
			// A concurrent delivery of the same message already recorded it.
		}
	}

}
