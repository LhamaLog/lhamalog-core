package br.com.lhamalog.core.usage.application;

import java.util.List;
import java.util.Optional;

public interface PlanSubscriptionRepository {
	Optional<PlanSubscription> findByTenantIdAndProductId(String tenantId, String productId);

	/**
	 * All subscriptions for a tenant (one per product). Used for tenant-level retention.
	 * Default is empty so single-method test doubles remain valid.
	 */
	default List<PlanSubscription> findByTenantId(String tenantId) {
		return List.of();
	}
}
