package br.com.lhamalog.core.dashboard.infrastructure;

import br.com.lhamalog.core.dashboard.application.UsageMetricsPort;
import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.application.PlanSubscription;
import br.com.lhamalog.core.usage.application.PlanSubscriptionRepository;
import br.com.lhamalog.core.usage.application.PlanTier;
import br.com.lhamalog.core.usage.application.TenantRetentionResolver;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository.StoredUsageFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UsageMetricsAdapter implements UsageMetricsPort {

	/** Binary GiB → bytes (2^30). Dashboard API never uses decimal 10^9. */
	static final BigDecimal BYTES_PER_GIB = BigDecimal.valueOf(1L << 30);

	private final UsageFactRepository usageFacts;
	private final PlanSubscriptionRepository subscriptions;
	private final TenantRetentionResolver retentionResolver;

	public UsageMetricsAdapter(UsageFactRepository usageFacts, PlanSubscriptionRepository subscriptions,
			TenantRetentionResolver retentionResolver) {
		this.usageFacts = usageFacts;
		this.subscriptions = subscriptions;
		this.retentionResolver = retentionResolver;
	}

	@Override
	public UsageSnapshot forProduct(String tenantId, String productId, Instant from, Instant to) {
		List<StoredUsageFact> facts = usageFacts.find(tenantId, productId, from, to);
		long volumeUsedBytes = facts.stream()
				.map(StoredUsageFact::request)
				.filter(request -> request.domain() == UsageDomain.LOGS)
				.mapToLong(request -> request.acceptedBytes())
				.sum();
		long traces = facts.stream()
				.map(StoredUsageFact::request)
				.filter(request -> request.domain() == UsageDomain.TRACES)
				.mapToLong(request -> request.acceptedRecordCount())
				.sum();
		Long volumeIngestaoBytes = volumeIngestaoBytes(tenantId, productId);
		return new UsageSnapshot(volumeUsedBytes, traces, volumeIngestaoBytes, retentionDays(tenantId));
	}

	@Override
	public int retentionDays(String tenantId) {
		Integer resolved = retentionResolver.resolveRetentionDays(tenantId);
		return resolved == null ? PlanTier.FREE.retentionDays() : resolved;
	}

	private Long volumeIngestaoBytes(String tenantId, String productId) {
		return subscriptions.findByTenantIdAndProductId(tenantId, productId)
				.map(UsageMetricsAdapter::toIngestaoBytes)
				.orElse(0L);
	}

	static Long toIngestaoBytes(PlanSubscription subscription) {
		if (subscription.unlimitedUsage()) {
			return null;
		}
		BigDecimal gib = subscription.allowanceGigabytes() == null
				? BigDecimal.ZERO
				: subscription.allowanceGigabytes();
		return gib.multiply(BYTES_PER_GIB).setScale(0, RoundingMode.DOWN).longValueExact();
	}
}
