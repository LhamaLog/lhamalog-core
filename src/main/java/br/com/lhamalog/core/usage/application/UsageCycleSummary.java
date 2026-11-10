package br.com.lhamalog.core.usage.application;

import java.time.Instant;

public record UsageCycleSummary(Instant from, Instant to, long acceptedRecordCount, long acceptedBytes,
		double acceptedGigabytes) {}
