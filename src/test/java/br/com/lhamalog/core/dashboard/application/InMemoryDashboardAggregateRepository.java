package br.com.lhamalog.core.dashboard.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryDashboardAggregateRepository implements DashboardAggregateRepository {

	private final Map<String, StoredAggregate> aggregates = new ConcurrentHashMap<>();
	private final Map<String, Instant> watermarks = new ConcurrentHashMap<>();
	private final List<ProductScope> scopes = new ArrayList<>();

	void setScopes(List<ProductScope> productScopes) {
		scopes.clear();
		scopes.addAll(productScopes);
	}

	@Override
	public void upsert(LogCountBucket bucket, String tenantId, String productId, Instant consolidatedAt) {
		String id = tenantId + "|" + productId + "|" + bucket.severity() + "|" + bucket.serviceName() + "|"
				+ bucket.hourStart();
		aggregates.put(id, new StoredAggregate(tenantId, productId, bucket.severity(), bucket.serviceName(),
				bucket.hourStart(), bucket.count(), consolidatedAt));
	}

	@Override
	public List<StoredAggregate> findInRange(String tenantId, String productId, Instant fromInclusive,
			Instant toExclusive) {
		return aggregates.values().stream()
				.filter(aggregate -> aggregate.tenantId().equals(tenantId)
						&& aggregate.productId().equals(productId)
						&& !aggregate.hourStart().isBefore(fromInclusive)
						&& aggregate.hourStart().isBefore(toExclusive))
				.toList();
	}

	@Override
	public Optional<Instant> findWatermark(String tenantId) {
		return Optional.ofNullable(watermarks.get(tenantId));
	}

	@Override
	public void saveWatermark(String tenantId, Instant dadosConsolidadosAte, Instant updatedAt) {
		watermarks.put(tenantId, dadosConsolidadosAte);
	}

	@Override
	public List<ProductScope> listActiveProductScopes() {
		return List.copyOf(scopes);
	}

	List<StoredAggregate> all() {
		return List.copyOf(aggregates.values());
	}
}
