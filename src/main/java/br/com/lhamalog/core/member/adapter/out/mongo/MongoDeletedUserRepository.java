package br.com.lhamalog.core.member.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.member.application.port.out.DeletedUserRepository;

/** MongoDB adapter of {@link DeletedUserRepository}. */
@Repository
public class MongoDeletedUserRepository implements DeletedUserRepository {

	private final MongoTemplate mongoTemplate;

	public MongoDeletedUserRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public boolean isDeleted(String clerkUserId) {
		return mongoTemplate.exists(byId(clerkUserId), DeletedUserDocument.class);
	}

	@Override
	public void markDeleted(String clerkUserId, Instant deletedAt) {
		mongoTemplate.upsert(byId(clerkUserId),
				new Update().setOnInsert("deletedAt", deletedAt).setOnInsert("tenantId", null),
				DeletedUserDocument.class);
	}

	private static Query byId(String clerkUserId) {
		return Query.query(Criteria.where("_id").is(clerkUserId));
	}

}
