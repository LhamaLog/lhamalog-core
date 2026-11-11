package br.com.lhamalog.core.usage.application;

import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoPlanSubscriptionRepository implements PlanSubscriptionRepository {
	private final MongoTemplate mongo;

	public MongoPlanSubscriptionRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Optional<PlanSubscription> findByTenantIdAndProductId(String tenantId, String productId) {
		return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("tenantId").is(tenantId)
				.and("productId").is(productId)), PlanSubscription.class));
	}

	public PlanSubscription save(PlanSubscription subscription) {
		return mongo.save(subscription);
	}
}
