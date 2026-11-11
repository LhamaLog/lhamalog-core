package br.com.lhamalog.core.usage.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("lhamalog.usage")
public record QuotaReservationProperties(@DefaultValue("20m") Duration reservationTtl,
		@DefaultValue("30s") Duration reservationReaperInterval) {
	public Duration ttl() {
		return reservationTtl;
	}

	public QuotaReservationProperties {
		if (reservationTtl == null || reservationTtl.isNegative() || reservationTtl.isZero()
				|| reservationReaperInterval == null || reservationReaperInterval.isNegative()
				|| reservationReaperInterval.isZero()) {
			throw new IllegalArgumentException("reservation durations must be positive");
		}
	}
}
