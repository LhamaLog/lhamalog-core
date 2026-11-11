package br.com.lhamalog.core.usage.application;

public record DomainUsageSummary(long acceptedRecordCount, long acceptedBytes, double acceptedGigabytes) {
	public static DomainUsageSummary of(long acceptedRecordCount, long acceptedBytes) {
		return new DomainUsageSummary(acceptedRecordCount, acceptedBytes, acceptedBytes / 1_000_000_000d);
	}

	public static DomainUsageSummary empty() {
		return of(0L, 0L);
	}
}
