package br.com.lhamalog.core.occurrence.infrastructure;

import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import br.com.lhamalog.core.occurrence.application.OccurrenceCommandRepository;
import br.com.lhamalog.core.occurrence.application.OccurrenceWriteRepository;

/** {@link OccurrenceCommandRepository} with conditional {@code updateOne} on the raw collection (spec 014, R2). */
@Repository
public class MongoOccurrenceCommandRepository implements OccurrenceCommandRepository {

	private final MongoTemplate mongo;

	public MongoOccurrenceCommandRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public boolean changeStatus(String tenantId, String id, String expectedRevision, String status, String from,
			Instant changedAt, String userId, String newRevision) {
		Document change = new Document("from", from).append("to", status)
			.append("changedAt", Date.from(changedAt))
			.append("changedBy", user(userId));
		return update(tenantId, id, expectedRevision, Updates.combine(Updates.set("status", status),
				Updates.push("statusHistory", change), Updates.set("revision", newRevision)));
	}

	@Override
	public boolean archive(String tenantId, String id, String expectedRevision, Instant archivedAt, String userId,
			String newRevision) {
		return update(tenantId, id, expectedRevision, Updates.combine(Updates.set("archivedAt", Date.from(archivedAt)),
				Updates.set("archivedBy", user(userId)), Updates.set("revision", newRevision)));
	}

	@Override
	public boolean unarchive(String tenantId, String id, String expectedRevision, String newRevision) {
		return update(tenantId, id, expectedRevision, Updates.combine(Updates.unset("archivedAt"),
				Updates.unset("archivedBy"), Updates.set("revision", newRevision)));
	}

	private boolean update(String tenantId, String id, String expectedRevision, Bson update) {
		Bson filter = Filters.and(Filters.eq("_id", id), Filters.eq("tenantId", tenantId), revisionIs(expectedRevision));
		return mongo.getCollection(MongoOccurrenceWriteRepository.COLLECTION).updateOne(filter, update)
			.getModifiedCount() == 1;
	}

	/** A document written without revision matches {@code sem-revisao} (spec 013, research R3). */
	private static Bson revisionIs(String revision) {
		if (OccurrenceWriteRepository.NO_REVISION.equals(revision)) {
			return Filters.or(Filters.exists("revision", false), Filters.eq("revision", null), Filters.eq("revision", ""));
		}
		return Filters.eq("revision", revision);
	}

	private static Document user(String userId) {
		return new Document("type", "USER").append("id", userId);
	}

}
