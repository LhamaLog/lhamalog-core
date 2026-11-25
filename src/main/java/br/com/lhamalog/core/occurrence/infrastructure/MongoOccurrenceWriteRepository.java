package br.com.lhamalog.core.occurrence.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;
import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.occurrence.application.OccurrenceWriteRepository;

/**
 * {@link OccurrenceWriteRepository} on the raw driver: the mapped writes of Spring Data refuse map keys with dots,
 * which extracted fields have ({@code http.route}). The unique index {@code occurrence_unique_key} of the spec 012
 * guards concurrent creations of the same key.
 */
@Repository
public class MongoOccurrenceWriteRepository implements OccurrenceWriteRepository {

	static final String COLLECTION = "Ocorrencia";

	private static final Logger log = LoggerFactory.getLogger(MongoOccurrenceWriteRepository.class);

	private final MongoTemplate mongo;

	public MongoOccurrenceWriteRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Map<OccurrenceKey, Document> findByKeys(String tenantId, String productId, Collection<OccurrenceKey> keys) {
		Map<OccurrenceKey, Document> found = new HashMap<>();
		if (keys.isEmpty()) return found;
		List<String> signatures = keys.stream().map(OccurrenceKey::signature).distinct().toList();
		Bson filter = Filters.and(Filters.eq("tenantId", tenantId), Filters.eq("productId", productId),
				Filters.in("signature", signatures));
		for (Document document : collection().find(filter)) {
			if (!(document.get("signature") instanceof String signature) || !(document.get("type") instanceof String type)) {
				continue;
			}
			OccurrenceKey key = new OccurrenceKey(signature, type);
			if (!keys.contains(key)) continue;
			if (found.putIfAbsent(key, document) != null) {
				log.error("event=occurrence.lookup.duplicated | More than one occurrence for the same key | tenantId={} productId={} occurrenceIds={},{}",
						tenantId, productId, found.get(key).get("_id"), document.get("_id"));
				throw new InternalApiException(HttpStatus.INTERNAL_SERVER_ERROR, InternalApiException.INVALID_OCCURRENCE);
			}
		}
		return found;
	}

	@Override
	public boolean replaceIfRevision(String id, String expectedRevision, Document after) {
		return collection().replaceOne(Filters.and(Filters.eq("_id", id), revisionIs(expectedRevision)), after)
			.getMatchedCount() == 1;
	}

	@Override
	public boolean insertNew(Document after) {
		try {
			collection().insertOne(after);
			return true;
		}
		catch (MongoWriteException failure) {
			if (ErrorCategory.fromErrorCode(failure.getError().getCode()) == ErrorCategory.DUPLICATE_KEY) return false;
			throw failure;
		}
	}

	@Override
	public boolean restoreIfRevision(String id, String writtenRevision, Document before) {
		return replaceIfRevision(id, writtenRevision, before);
	}

	@Override
	public boolean deleteIfRevision(String id, String writtenRevision) {
		return collection().deleteOne(Filters.and(Filters.eq("_id", id), Filters.eq("revision", writtenRevision)))
			.getDeletedCount() == 1;
	}

	/** A document written without revision matches {@link #NO_REVISION} (research R3). */
	private static Bson revisionIs(String revision) {
		if (NO_REVISION.equals(revision)) {
			return Filters.or(Filters.exists("revision", false), Filters.eq("revision", null), Filters.eq("revision", ""));
		}
		return Filters.eq("revision", revision);
	}

	private MongoCollection<Document> collection() {
		return mongo.getCollection(COLLECTION);
	}

}
