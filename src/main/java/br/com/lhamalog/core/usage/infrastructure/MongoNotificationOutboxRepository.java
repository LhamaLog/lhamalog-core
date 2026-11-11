package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.LowBalanceNotification;
import br.com.lhamalog.core.usage.application.NotificationOutboxRepository;
import br.com.lhamalog.core.usage.application.NotificationOutboxState;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoNotificationOutboxRepository implements NotificationOutboxRepository {
	private final MongoTemplate mongo;

	public MongoNotificationOutboxRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public boolean saveIfAbsent(LowBalanceNotification notification) {
		try {
			mongo.insert(toDocument(notification));
			return true;
		}
		catch (DuplicateKeyException duplicate) {
			return false;
		}
	}

	@Override
	public List<LowBalanceNotification> findPending() {
		return mongo.find(Query.query(Criteria.where("state").is(NotificationOutboxState.PENDING)),
				NotificationOutboxDocument.class).stream().map(this::fromDocument).toList();
	}

	@Override
	public boolean markPublished(String idempotencyKey, Instant publishedAt) {
		UpdateResult result = mongo.updateFirst(Query.query(Criteria.where("_id").is(idempotencyKey)
				.and("state").is(NotificationOutboxState.PENDING)),
				new Update().set("state", NotificationOutboxState.PUBLISHED).set("publishedAt", publishedAt),
				NotificationOutboxDocument.class);
		return result.getModifiedCount() == 1;
	}

	private static NotificationOutboxDocument toDocument(LowBalanceNotification value) {
		return new NotificationOutboxDocument(value.idempotencyKey(), value.tenantId(), value.productId(), value.cycleStart(),
				value.threshold(), value.remainingBytes(), value.type(), value.severity(), value.mandatoryRoles(),
				value.mandatoryChannels(), value.state(), value.createdAt(), value.publishedAt());
	}

	private LowBalanceNotification fromDocument(NotificationOutboxDocument value) {
		return new LowBalanceNotification(value.idempotencyKey(), value.tenantId(), value.productId(), value.cycleStart(),
				value.threshold(), value.remainingBytes(), value.type(), value.severity(), value.mandatoryRoles(),
				value.mandatoryChannels(), value.state(), value.createdAt(), value.publishedAt());
	}
}
