package br.com.lhamalog.core.usage.application;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional overrides for ADR_131 plan retention days
 * ({@code lhamalog.plans.retention-days.<tier>}).
 */
@ConfigurationProperties("lhamalog.plans")
public record PlanRetentionProperties(@DefaultValue Map<String, Integer> retentionDays) {

	public int retentionDaysFor(PlanTier tier) {
		if (retentionDays != null && !retentionDays.isEmpty()) {
			Integer override = lookup(tier.name());
			if (override == null) {
				override = lookup(tier.name().toLowerCase(Locale.ROOT));
			}
			if (override != null && override > 0) {
				return override;
			}
		}
		return tier.retentionDays();
	}

	private Integer lookup(String key) {
		return retentionDays.get(key);
	}
}
