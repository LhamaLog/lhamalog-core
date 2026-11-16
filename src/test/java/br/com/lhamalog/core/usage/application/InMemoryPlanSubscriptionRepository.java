package br.com.lhamalog.core.usage.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPlanSubscriptionRepository implements PlanSubscriptionRepository {
	private final Map<String, PlanSubscription> subscriptions = new ConcurrentHashMap<>();

	public PlanSubscription save(PlanSubscription subscription) {
		subscriptions.put(key(subscription.tenantId(), subscription.productId()), subscription);
		return subscription;
	}

	@Override
	public Optional<PlanSubscription> findByTenantIdAndProductId(String tenantId, String productId) {
		return Optional.ofNullable(subscriptions.get(key(tenantId, productId)));
	}

	@Override
	public List<PlanSubscription> findByTenantId(String tenantId) {
		return subscriptions.values().stream()
				.filter(subscription -> subscription.tenantId().equals(tenantId))
				.toList();
	}

	private static String key(String tenantId, String productId) {
		return tenantId + "\u0000" + productId;
	}
}
