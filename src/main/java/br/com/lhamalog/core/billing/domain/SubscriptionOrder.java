package br.com.lhamalog.core.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import br.com.lhamalog.core.usage.application.PlanTier;

/**
 * Intent of an organization administrator to buy a plan (spec 010). Its id travels as the gateway charge
 * {@code externalReference}, which is how a payment confirmation finds the order.
 */
public record SubscriptionOrder(String orderId, String clerkOrgId, String clerkUserId, String companyName,
		PlanTier planTier, BigDecimal price, OrderStatus status, String gatewayChargeId, String paymentUrl,
		Instant createdAt, Instant paidAt, Instant canceledAt) {

	public SubscriptionOrder {
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(clerkOrgId, "clerkOrgId");
		Objects.requireNonNull(clerkUserId, "clerkUserId");
		Objects.requireNonNull(companyName, "companyName");
		Objects.requireNonNull(planTier, "planTier");
		Objects.requireNonNull(price, "price");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	public static SubscriptionOrder pending(String orderId, String clerkOrgId, String clerkUserId, String companyName,
			Plan plan, Instant now) {
		return new SubscriptionOrder(orderId, clerkOrgId, clerkUserId, companyName, plan.tier(), plan.price(),
				OrderStatus.PENDING, null, null, now, null, null);
	}

	public SubscriptionOrder withCharge(GatewayCharge charge) {
		return new SubscriptionOrder(orderId, clerkOrgId, clerkUserId, companyName, planTier, price, status,
				charge.chargeId(), charge.paymentUrl(), createdAt, paidAt, canceledAt);
	}

	public boolean isPaid() {
		return status == OrderStatus.PAID;
	}

}
