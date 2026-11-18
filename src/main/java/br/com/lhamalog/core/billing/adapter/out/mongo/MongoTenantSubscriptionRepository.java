package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.billing.application.port.out.TenantSubscriptionRepository;
import br.com.lhamalog.core.billing.domain.TenantSubscription;
import br.com.lhamalog.core.usage.application.PlanTier;
import br.com.lhamalog.core.usage.application.SubscriptionStatus;

/** MongoDB adapter of {@link TenantSubscriptionRepository}. */
@Repository
public class MongoTenantSubscriptionRepository implements TenantSubscriptionRepository {

	private final MongoTemplate mongoTemplate;

	public MongoTenantSubscriptionRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void createIfAbsent(TenantSubscription subscription) {
		Update update = new Update().setOnInsert("planTier", subscription.planTier().name())
			.setOnInsert("status", subscription.status().name())
			.setOnInsert("orderId", subscription.orderId())
			.setOnInsert("startedAt", subscription.startedAt());
		mongoTemplate.upsert(Query.query(Criteria.where("_id").is(subscription.tenantId())), update,
				TenantSubscriptionDocument.class);
	}

	@Override
	public Optional<TenantSubscription> findByTenantId(String tenantId) {
		return Optional.ofNullable(mongoTemplate.findById(tenantId, TenantSubscriptionDocument.class))
			.map((document) -> new TenantSubscription(document.tenantId(), PlanTier.valueOf(document.planTier()),
					SubscriptionStatus.fromPersisted(document.status()), document.orderId(), document.startedAt()));
	}

}
