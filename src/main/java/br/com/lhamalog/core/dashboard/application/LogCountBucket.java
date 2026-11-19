package br.com.lhamalog.core.dashboard.application;

import java.time.Instant;

/** One aggregated log count cell (hour × severity × service). */
public record LogCountBucket(Instant hourStart, LogSeverity severity, String serviceName, long count) {
	public LogCountBucket {
		if (hourStart == null) {
			throw new IllegalArgumentException("hourStart is required");
		}
		if (severity == null) {
			throw new IllegalArgumentException("severity is required");
		}
		serviceName = (serviceName == null || serviceName.isBlank()) ? "unknown" : serviceName.trim();
		if (count < 0) {
			throw new IllegalArgumentException("count must be >= 0");
		}
	}
}
