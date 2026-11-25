package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.domain.OrderStatus;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.usage.application.PlanTier;

/** MongoDB adapter of {@link SubscriptionOrderRepository}. Creates its indexes explicitly on startup (idempotent). */
@Repository
public class MongoSubscriptionOrderRepository implements SubscriptionOrderRepository, InitializingBean {

	static final String ORG_CREATED_INDEX = "subscription_order_org_created";

	static final String CHARGE_INDEX = "subscription_order_charge";

	static final String ONE_PENDING_INDEX = "subscription_order_one_pending";

	/**
	 * Attempts of {@link #replacePending} when concurrent purchases of the same organization collide. Every round has
	 * one winner, so this covers that many simultaneous purchases of one organization.
	 */
	static final int REPLACE_ATTEMPTS = 10;

	private final MongoTemplate mongoTemplate;

	public MongoSubscriptionOrderRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void afterPropertiesSet() {
		var indexes = mongoTemplate.indexOps(SubscriptionOrderDocument.class);
		indexes.createIndex(new Index().on(SubscriptionOrderDocument.CLERK_ORG_ID, Sort.Direction.ASC)
			.on(SubscriptionOrderDocument.CREATED_AT, Sort.Direction.DESC)
			.named(ORG_CREATED_INDEX));
		indexes.createIndex(new Index().on(SubscriptionOrderDocument.GATEWAY_CHARGE_ID, Sort.Direction.ASC)
			.named(CHARGE_INDEX));
		// At most one PENDING order per organization (FR-004), even with concurrent purchases.
		indexes.createIndex(new Index().on(SubscriptionOrderDocument.CLERK_ORG_ID, Sort.Direction.ASC)
			.unique()
			.partial(PartialIndexFilter
				.of(Criteria.where(SubscriptionOrderDocument.STATUS).is(OrderStatus.PENDING.name())))
			.named(ONE_PENDING_INDEX));
	}

	/** Cancels the pending order and inserts the new one; a concurrent purchase that won is canceled in turn. */
	@Override
	public void replacePending(SubscriptionOrder order, Instant now) {
		for (int attempt = 1;; attempt++) {
			cancelPending(order.clerkOrgId(), now);
			try {
				insert(order);
				return;
			}
			catch (DuplicateKeyException ex) {
				if (attempt >= REPLACE_ATTEMPTS) {
					throw ex;
				}
			}
		}
	}

	@Override
	public void insert(SubscriptionOrder order) {
		mongoTemplate.insert(toDocument(order));
	}

	@Override
	public void cancelPending(String clerkOrgId, Instant now) {
		Query query = Query.query(Criteria.where(SubscriptionOrderDocument.CLERK_ORG_ID)
			.is(clerkOrgId)
			.and(SubscriptionOrderDocument.STATUS)
			.is(OrderStatus.PENDING.name()));
		Update update = new Update().set(SubscriptionOrderDocument.STATUS, OrderStatus.CANCELED.name())
			.set("canceledAt", now);
		mongoTemplate.updateMulti(query, update, SubscriptionOrderDocument.class);
	}

	@Override
	public Optional<SubscriptionOrder> findById(String orderId) {
		return Optional.ofNullable(mongoTemplate.findById(orderId, SubscriptionOrderDocument.class))
			.map(MongoSubscriptionOrderRepository::toDomain);
	}

	@Override
	public Optional<SubscriptionOrder> findLatestByClerkOrgId(String clerkOrgId) {
		Query query = Query.query(Criteria.where(SubscriptionOrderDocument.CLERK_ORG_ID).is(clerkOrgId))
			.with(Sort.by(Sort.Direction.DESC, SubscriptionOrderDocument.CREATED_AT))
			.limit(1);
		return Optional.ofNullable(mongoTemplate.findOne(query, SubscriptionOrderDocument.class))
			.map(MongoSubscriptionOrderRepository::toDomain);
	}

	@Override
	public Optional<SubscriptionOrder> findByGatewayChargeId(String gatewayChargeId) {
		Query query = Query.query(Criteria.where(SubscriptionOrderDocument.GATEWAY_CHARGE_ID).is(gatewayChargeId));
		return Optional.ofNullable(mongoTemplate.findOne(query, SubscriptionOrderDocument.class))
			.map(MongoSubscriptionOrderRepository::toDomain);
	}

	@Override
	public void markPaid(String orderId, Instant now) {
		Query query = Query.query(Criteria.where("_id")
			.is(orderId)
			.and(SubscriptionOrderDocument.STATUS)
			.ne(OrderStatus.PAID.name()));
		Update update = new Update().set(SubscriptionOrderDocument.STATUS, OrderStatus.PAID.name()).set("paidAt", now);
		mongoTemplate.updateFirst(query, update, SubscriptionOrderDocument.class);
	}

	private static SubscriptionOrderDocument toDocument(SubscriptionOrder order) {
		return new SubscriptionOrderDocument(order.orderId(), order.clerkOrgId(), order.clerkUserId(),
				order.companyName(), order.planTier().name(), order.price(), order.status().name(),
				order.gatewayChargeId(), order.paymentUrl(), order.createdAt(), order.paidAt(), order.canceledAt());
	}

	private static SubscriptionOrder toDomain(SubscriptionOrderDocument document) {
		return new SubscriptionOrder(document.orderId(), document.clerkOrgId(), document.clerkUserId(),
				document.companyName(), PlanTier.valueOf(document.planTier()), document.price(),
				OrderStatus.valueOf(document.status()), document.gatewayChargeId(), document.paymentUrl(),
				document.createdAt(), document.paidAt(), document.canceledAt());
	}

}
