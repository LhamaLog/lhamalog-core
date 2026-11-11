package br.com.lhamalog.core.usage.infrastructure;

import com.mongodb.MongoWriteException;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoUsageFactRepository implements UsageFactRepository {
	private static final int DUPLICATE_KEY_CODE = 11000;

	private final MongoTemplate mongo;

	public MongoUsageFactRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public SaveResult saveIfAbsent(StoredUsageFact fact) {
		try {
			mongo.insert(UsageFactDocumentMapper.toIdempotency(fact));
		}
		catch (RuntimeException exception) {
			if (!isDuplicateKey(exception)) {
				throw exception;
			}
			return resolveDuplicate(fact);
		}
		try {
			mongo.insert(UsageFactDocumentMapper.toDocument(fact));
		}
		catch (RuntimeException exception) {
			mongo.remove(Query.query(Criteria.where("_id").is(fact.request().usageFactId())),
					UsageFactIdempotencyDocument.class);
			throw exception;
		}
		return SaveResult.CREATED;
	}

	private SaveResult resolveDuplicate(StoredUsageFact fact) {
		UsageFactIdempotencyDocument existing = mongo.findById(fact.request().usageFactId(),
				UsageFactIdempotencyDocument.class);
		if (existing == null) {
			return saveIfAbsent(fact);
		}
		return UsageFactDocumentMapper.toStored(existing).semanticallyEquals(fact)
				? SaveResult.DUPLICATE : SaveResult.CONFLICT;
	}

	static boolean isDuplicateKey(Throwable exception) {
		MongoWriteException write = findMongoWriteException(exception);
		if (write != null) {
			return write.getError().getCode() == DUPLICATE_KEY_CODE;
		}
		return exception instanceof DuplicateKeyException;
	}

	private static MongoWriteException findMongoWriteException(Throwable exception) {
		while (exception != null) {
			if (exception instanceof MongoWriteException write) {
				return write;
			}
			exception = exception.getCause();
		}
		return null;
	}

	@Override
	public List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to) {
		Query query = windowQuery(tenantId, productId, from, to, null)
				.with(Sort.by(Sort.Order.asc("acceptedAt"), Sort.Order.asc("_id")));
		return mongo.find(query, UsageFactDocument.class).stream()
				.map(UsageFactDocumentMapper::toStored).toList();
	}

	@Override
	public Page findPage(String tenantId, String productId, Instant from, Instant to, int limit, String cursor) {
		UsageFactCursor decoded = cursor == null || cursor.isBlank() ? null : UsageFactCursor.decode(cursor);
		Query query = windowQuery(tenantId, productId, from, to, decoded)
				.with(Sort.by(Sort.Order.asc("acceptedAt"), Sort.Order.asc("_id")))
				.limit(limit + 1);
		List<UsageFactDocument> documents = mongo.find(query, UsageFactDocument.class);
		boolean hasMore = documents.size() > limit;
		List<UsageFactDocument> page = hasMore ? documents.subList(0, limit) : documents;
		String nextCursor = null;
		if (hasMore && !page.isEmpty()) {
			UsageFactDocument last = page.get(page.size() - 1);
			nextCursor = new UsageFactCursor(last.acceptedAt(), last.id()).encode();
		}
		return new Page(page.stream().map(UsageFactDocumentMapper::toStored).toList(), nextCursor);
	}

	private static Query windowQuery(String tenantId, String productId, Instant from, Instant to,
			UsageFactCursor cursor) {
		Criteria window = Criteria.where("tenantId").is(tenantId)
				.and("productId").is(productId)
				.and("acceptedAt").gte(from).lt(to);
		if (cursor == null) {
			return Query.query(window);
		}
		Criteria afterCursor = new Criteria().orOperator(
				Criteria.where("acceptedAt").gt(cursor.acceptedAt()),
				Criteria.where("acceptedAt").is(cursor.acceptedAt()).and("_id").gt(cursor.id()));
		return Query.query(new Criteria().andOperator(window, afterCursor));
	}
}
