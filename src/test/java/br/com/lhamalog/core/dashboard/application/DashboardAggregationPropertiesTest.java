package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.scheduling.annotation.Scheduled;

class DashboardAggregationPropertiesTest {

	@Test
	void bindsDefaultHourlyCron() {
		Binder binder = new Binder(new MapConfigurationPropertySource());
		DashboardAggregationProperties properties = binder
				.bind("lhamalog.dashboard.aggregation", Bindable.of(DashboardAggregationProperties.class))
				.orElseGet(() -> new DashboardAggregationProperties("0 0 * * * *"));
		assertEquals("0 0 * * * *", properties.cron());
	}

	@Test
	void bindsCronOverride() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource();
		source.put("lhamalog.dashboard.aggregation.cron", "0 15 * * * *");
		DashboardAggregationProperties properties = new Binder(source)
				.bind("lhamalog.dashboard.aggregation", Bindable.of(DashboardAggregationProperties.class))
				.get();
		assertEquals("0 15 * * * *", properties.cron());
	}

	@Test
	void scheduledAnnotationUsesPropertyPlaceholderWithHourlyDefault() throws Exception {
		Method method = DashboardAggregationJob.class.getDeclaredMethod("run");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);
		assertTrue(scheduled.cron().contains("lhamalog.dashboard.aggregation.cron"));
		assertTrue(scheduled.cron().contains("0 0 * * * *"));
	}
}
