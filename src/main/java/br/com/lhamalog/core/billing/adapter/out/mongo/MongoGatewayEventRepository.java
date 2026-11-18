package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.billing.application.port.out.GatewayEventRepository;
import br.com.lhamalog.core.billing.domain.GatewayEvent;
import br.com.lhamalog.core.billing.domain.GatewayEventStatus;

/** MongoDB adapter of {@link GatewayEventRepository}. The {@code _id} makes each gateway event unique. */
@Repository
public class MongoGatewayEventRepository implements GatewayEventRepository {

	private final MongoTemplate mongoTemplate;

	public MongoGatewayEventRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Optional<GatewayEvent> findById(String eventId) {
		return Optional.ofNullable(mongoTemplate.findById(eventId, GatewayEventDocument.class))
			.map(MongoGatewayEventRepository::toDomain);
	}

	@Override
	public boolean insertReceived(GatewayEvent event) {
		try {
			mongoTemplate.insert(toDocument(event));
			return true;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	@Override
	public boolean claimRetry(String eventId, Instant now) {
		Query query = Query.query(
				Criteria.where("_id").is(eventId).and(GatewayEventDocument.STATUS).is(GatewayEventStatus.FAILED.name()));
		Update update = new Update().set(GatewayEventDocument.STATUS, GatewayEventStatus.RECEIVED.name())
			.inc("attempts", 1);
		return mongoTemplate.updateFirst(query, update, GatewayEventDocument.class).getModifiedCount() == 1;
	}

	@Override
	public void complete(String eventId, GatewayEventStatus status, String reason, String tenantId, Instant now) {
		Update update = new Update().set(GatewayEventDocument.STATUS, status.name())
			.set("reason", reason)
			.set("tenantId", tenantId)
			.set("processedAt", now);
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)), update, GatewayEventDocument.class);
	}

	private static GatewayEventDocument toDocument(GatewayEvent event) {
		return new GatewayEventDocument(event.eventId(), event.gateway(), event.type(), event.orderId(),
				event.chargeId(), event.status().name(), event.reason(), event.tenantId(), event.attempts(),
				event.receivedAt(), event.processedAt());
	}

	private static GatewayEvent toDomain(GatewayEventDocument document) {
		return new GatewayEvent(document.eventId(), document.gateway(), document.type(), document.orderId(),
				document.chargeId(), GatewayEventStatus.valueOf(document.status()), document.reason(),
				document.tenantId(), document.attempts(), document.receivedAt(), document.processedAt());
	}

}
