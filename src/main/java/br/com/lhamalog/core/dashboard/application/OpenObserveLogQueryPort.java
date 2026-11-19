package br.com.lhamalog.core.dashboard.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Reads log counts from OpenObserve ({@code POST /api/{org}/_search} + SQL). */
public interface OpenObserveLogQueryPort {

	/**
	 * Counts logs in {@code [from, to)} grouped by truncated hour, severity and service.
	 * Severities in the result are already ADR_170-normalized; unknown raw levels are skipped.
	 */
	List<LogCountBucket> countLogs(String tenantId, String productId, Instant from, Instant to,
			Set<LogSeverity> severities);
}
