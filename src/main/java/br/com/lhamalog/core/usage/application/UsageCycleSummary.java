package br.com.lhamalog.core.usage.application;

import java.time.Instant;

public record UsageCycleSummary(boolean active, Instant from, Instant to, DomainUsageSummary logs,
		DomainUsageSummary traces) {
	public static UsageCycleSummary inactive() {
		return new UsageCycleSummary(false, null, null, DomainUsageSummary.empty(), DomainUsageSummary.empty());
	}

	public static UsageCycleSummary active(Instant from, Instant to, DomainUsageSummary logs,
			DomainUsageSummary traces) {
		return new UsageCycleSummary(true, from, to, logs, traces);
	}
}
