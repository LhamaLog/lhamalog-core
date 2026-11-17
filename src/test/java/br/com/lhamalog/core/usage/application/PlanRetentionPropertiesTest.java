package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PlanRetentionPropertiesTest {

	@Test
	void defaultsToAdr131ValuesWhenUnconfigured() {
		PlanRetentionProperties properties = new Binder(new MapConfigurationPropertySource())
				.bindOrCreate("lhamalog.plans", Bindable.of(PlanRetentionProperties.class));
		assertThat(properties.retentionDaysFor(PlanTier.FREE)).isEqualTo(7);
		assertThat(properties.retentionDaysFor(PlanTier.PRO)).isEqualTo(30);
		assertThat(properties.retentionDaysFor(PlanTier.ENTERPRISE)).isEqualTo(90);
	}

	@Test
	void honorsConfiguredOverrides() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource();
		source.put("lhamalog.plans.retention-days.free", "10");
		source.put("lhamalog.plans.retention-days.PRO", "45");
		PlanRetentionProperties properties = new Binder(source)
				.bind("lhamalog.plans", Bindable.of(PlanRetentionProperties.class))
				.get();
		assertThat(properties.retentionDaysFor(PlanTier.FREE)).isEqualTo(10);
		assertThat(properties.retentionDaysFor(PlanTier.PRO)).isEqualTo(45);
		assertThat(properties.retentionDaysFor(PlanTier.STARTER)).isEqualTo(15);
	}
}
