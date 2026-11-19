package br.com.lhamalog.core.dashboard.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for SSID_011 hourly aggregates and per-tenant watermarks. */
public interface DashboardAggregateRepository {

	void upsert(LogCountBucket bucket, String tenantId, String productId, Instant consolidatedAt);

	List<StoredAggregate> findInRange(String tenantId, String productId, Instant fromInclusive,
			Instant toExclusive);

	Optional<Instant> findWatermark(String tenantId);

	void saveWatermark(String tenantId, Instant dadosConsolidadosAte, Instant updatedAt);

	List<ProductScope> listActiveProductScopes();

	record StoredAggregate(String tenantId, String productId, LogSeverity severity, String serviceName,
			Instant hourStart, long count, Instant consolidatedAt) {
	}

	record ProductScope(String tenantId, String productId) {
	}
}
