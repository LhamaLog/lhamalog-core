package br.com.lhamalog.core.billing.application.port.out;

import java.util.Optional;

import br.com.lhamalog.core.billing.domain.TenantSubscription;

/** Output port for tenant subscriptions (one per tenant). */
public interface TenantSubscriptionRepository {

	/** Stores the subscription unless the tenant already has one, which is kept as it is. */
	void createIfAbsent(TenantSubscription subscription);

	Optional<TenantSubscription> findByTenantId(String tenantId);

}
