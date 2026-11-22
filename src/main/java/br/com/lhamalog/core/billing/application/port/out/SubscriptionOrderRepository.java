package br.com.lhamalog.core.billing.application.port.out;

import java.time.Instant;
import java.util.Optional;

import br.com.lhamalog.core.billing.domain.SubscriptionOrder;

/** Output port for subscription orders. */
public interface SubscriptionOrderRepository {

	void insert(SubscriptionOrder order);

	/** Moves every PENDING order of the organization to CANCELED. */
	void cancelPending(String clerkOrgId, Instant now);

	/**
	 * Makes {@code order} the only PENDING order of its organization: cancels the pending ones, then inserts it
	 * (FR-004). Adapters that can enforce one pending order per organization must also resolve concurrent calls.
	 */
	default void replacePending(SubscriptionOrder order, Instant now) {
		cancelPending(order.clerkOrgId(), now);
		insert(order);
	}

	Optional<SubscriptionOrder> findById(String orderId);

	/** Most recent order of the organization, whatever its status. */
	Optional<SubscriptionOrder> findLatestByClerkOrgId(String clerkOrgId);

	Optional<SubscriptionOrder> findByGatewayChargeId(String gatewayChargeId);

	/** Marks the order PAID unless it already is (a paid order is never touched again). */
	void markPaid(String orderId, Instant now);

}
