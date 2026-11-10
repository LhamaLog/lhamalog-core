package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.api.UsageFactRequest;
import com.mongodb.MongoWriteException;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoUsageFactRepository implements UsageFactRepository {
	private final MongoTemplate mongo;

	public MongoUsageFactRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public SaveResult saveIfAbsent(StoredUsageFact fact) {
		try {
			mongo.insert(new UsageFactDocument(fact.request().usageFactId(), fact.request(), fact.receivedAt()));
			return SaveResult.CREATED;
		}
		catch (DuplicateKeyException | MongoWriteException exception) {
			UsageFactDocument existing = mongo.findById(fact.request().usageFactId(), UsageFactDocument.class);
			return existing != null && new StoredUsageFact(existing.request(), existing.receivedAt())
					.semanticallyEquals(fact) ? SaveResult.DUPLICATE : SaveResult.CONFLICT;
		}
	}

	@Override
	public List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to) {
		Query query = Query.query(Criteria.where("request.tenantId").is(tenantId)
				.and("request.productId").is(productId)
				.and("request.acceptedAt").gte(from).lt(to))
				.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "request.acceptedAt"));
		return mongo.find(query, UsageFactDocument.class).stream()
				.map(document -> new StoredUsageFact(document.request(), document.receivedAt())).toList();
	}

	@Document("UsageFact")
	record UsageFactDocument(@Id String id, UsageFactRequest request,
			@Indexed(expireAfter = "7d") Instant receivedAt) {}
}
