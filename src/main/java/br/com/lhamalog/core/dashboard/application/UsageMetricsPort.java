package br.com.lhamalog.core.dashboard.application;

import java.time.Instant;

/**
 * Volume, traces, retention and ingestion quota for dashboard responses.
 * Volume and quota are integers in bytes (GiB of plan × 2^30; never 10^9).
 */
public interface UsageMetricsPort {

	UsageSnapshot forProduct(String tenantId, String productId, Instant from, Instant to);

	int retentionDays(String tenantId);

	record UsageSnapshot(long volumeUsedBytes, long traces, Long volumeIngestaoBytes, int retentionDays) {
	}
}
