package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.infrastructure.QuotaBalanceDocument;
import br.com.lhamalog.core.usage.infrastructure.QuotaReservationDocument;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class UsageMongoIndexInitializer {
	private final MongoTemplate mongo;

	public UsageMongoIndexInitializer(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@PostConstruct
	public void ensureIndexes() {
		mongo.indexOps(UsageFactDocument.class).createIndex(new Index()
				.named("usage_fact_tenant_product_acceptedAt")
				.on("tenantId", Sort.Direction.ASC)
				.on("productId", Sort.Direction.ASC)
				.on("acceptedAt", Sort.Direction.ASC)
				.on("_id", Sort.Direction.ASC));
		mongo.indexOps(UsageFactIdempotencyDocument.class).createIndex(new Index()
				.named("usage_fact_idempotency_ttl")
				.on("receivedAt", Sort.Direction.ASC)
				.expire(7, TimeUnit.DAYS));
		mongo.indexOps(PlanSubscription.class).createIndex(new Index()
				.named("plan_subscription_tenant_product")
				.on("tenantId", Sort.Direction.ASC)
				.on("productId", Sort.Direction.ASC)
				.unique());
		mongo.indexOps(QuotaBalanceDocument.class).createIndex(new Index()
				.named("quota_balance_tenant_product_cycle")
				.on("tenantId", Sort.Direction.ASC)
				.on("productId", Sort.Direction.ASC)
				.on("cycleStart", Sort.Direction.ASC)
				.unique());
		mongo.indexOps(QuotaReservationDocument.class).createIndex(new Index()
				.named("quota_reservation_expiry")
				.on("expiresAt", Sort.Direction.ASC));
		mongo.indexOps(NotificationOutboxDocument.class).createIndex(new Index()
				.named("notification_outbox_tenant_product_cycle_threshold")
				.on("tenantId", Sort.Direction.ASC)
				.on("productId", Sort.Direction.ASC)
				.on("cycleStart", Sort.Direction.ASC)
				.on("threshold", Sort.Direction.ASC)
				.unique());
	}
}
