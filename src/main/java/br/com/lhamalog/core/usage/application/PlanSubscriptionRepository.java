package br.com.lhamalog.core.usage.application;

import java.util.Optional;

public interface PlanSubscriptionRepository {
	Optional<PlanSubscription> findByTenantIdAndProductId(String tenantId, String productId);
}
