package br.com.lhamalog.core.usage.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves tenant-level raw-log retention as the maximum {@code retentionDays} among the tenant's countable
 * subscriptions (decided by Michel on 16/11/2026 16:36).
 *
 * <p>Countable: {@link SubscriptionStatus#ATIVA} and {@link SubscriptionStatus#INADIMPLENTE} (including the
 * delinquency grace); {@link SubscriptionStatus#SUSPENSA} is excluded. A legacy subscription without a
 * {@link PlanTier} counts as {@link PlanTier#FREE} (7 days). The stored tier stays {@code null}; only the
 * resolution assumes FREE.
 *
 * <p>Returns {@code null} only when the tenant has no countable subscription (the Collector then leaves the
 * stream retention unchanged). The stream is per tenant while each product has its own subscription, so the
 * maximum is used and no product loses data.
 */
@Service
public class TenantRetentionResolver {
	private static final Logger log = LoggerFactory.getLogger(TenantRetentionResolver.class);

	private final PlanSubscriptionRepository subscriptions;
	private final PlanRetentionProperties retentionProperties;

	public TenantRetentionResolver(PlanSubscriptionRepository subscriptions,
			PlanRetentionProperties retentionProperties) {
		this.subscriptions = subscriptions;
		this.retentionProperties = retentionProperties;
	}

	/**
	 * @return max retention days for the tenant, or {@code null} when unknown
	 */
	public Integer resolveRetentionDays(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			log.warn("event=quota.retention.invalid_tenant | Retention resolve skipped for blank tenantId");
			return null;
		}
		List<PlanSubscription> tenantSubscriptions = subscriptions.findByTenantId(tenantId);
		int max = -1;
		for (PlanSubscription subscription : tenantSubscriptions) {
			if (subscription.status() == SubscriptionStatus.SUSPENSA) {
				continue;
			}
			PlanTier tier = subscription.planTier();
			if (tier == null) {
				tier = PlanTier.FREE;
				log.debug(
						"event=quota.retention.default_tier_applied | Subscription without plan tier counted as FREE | tenantId={} productId={}",
						tenantId, subscription.productId());
			}
			max = Math.max(max, retentionProperties.retentionDaysFor(tier));
		}
		Integer retentionDays = max < 0 ? null : max;
		log.debug(
				"event=quota.retention.resolved | Tenant retention resolved | tenantId={} retentionDays={} subscriptionCount={}",
				tenantId, retentionDays, tenantSubscriptions.size());
		return retentionDays;
	}
}
