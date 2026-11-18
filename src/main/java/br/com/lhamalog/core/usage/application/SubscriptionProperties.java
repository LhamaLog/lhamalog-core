package br.com.lhamalog.core.usage.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("lhamalog.subscription")
public record SubscriptionProperties(@DefaultValue("15d") Duration delinquencyGrace) {
	public SubscriptionProperties {
		if (delinquencyGrace == null || delinquencyGrace.isNegative() || delinquencyGrace.isZero()) {
			throw new IllegalArgumentException("delinquency grace must be positive");
		}
	}
}
