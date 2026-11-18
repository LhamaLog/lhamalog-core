package br.com.lhamalog.core.billing.domain;

import java.time.Instant;
import java.util.Objects;

import br.com.lhamalog.core.usage.application.PlanTier;
import br.com.lhamalog.core.usage.application.SubscriptionStatus;

/**
 * Subscription of a tenant (Assinatura, ADR_122): one per tenant, created when the payment is confirmed. Independent
 * of the per-product {@code PlanSubscription} of the usage module (research R6).
 */
public record TenantSubscription(String tenantId, PlanTier planTier, SubscriptionStatus status, String orderId,
		Instant startedAt) {

	public TenantSubscription {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(planTier, "planTier");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(startedAt, "startedAt");
	}

	public static TenantSubscription activated(String tenantId, SubscriptionOrder order, Instant now) {
		return new TenantSubscription(tenantId, order.planTier(), SubscriptionStatus.ACTIVE, order.orderId(), now);
	}

}
