package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OpenObservePropertiesTest {

	@Test
	void bindsDefaultsMatchingCollectorOpenObserveFields() {
		OpenObserveProperties properties = new Binder(new MapConfigurationPropertySource())
				.bind("lhamalog.openobserve", Bindable.of(OpenObserveProperties.class))
				.orElseGet(() -> new OpenObserveProperties(
						"http://localhost:5080", "lhamalog", null, "logs",
						"tenantid", "productid", "severitynumber", "severitytext", "servicename",
						null, null));
		assertEquals("logs", properties.logsStreamPrefix());
		assertEquals("tenantid", properties.tenantField());
		assertEquals("productid", properties.productField());
		assertEquals("severitynumber", properties.severityNumberField());
		assertEquals("severitytext", properties.severityTextField());
		assertEquals("servicename", properties.serviceField());
		assertFalse(properties.hasAuth());
		assertEquals("logs_llt187_dash_tenant_94439d83",
				properties.streamForTenant("llt187-dash-tenant"));
	}

	@Test
	void bindsFieldAndPrefixOverrides() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource();
		source.put("lhamalog.openobserve.logs-stream-prefix", "events");
		source.put("lhamalog.openobserve.tenant-field", "tenant_id");
		source.put("lhamalog.openobserve.product-field", "product_id");
		OpenObserveProperties properties = new Binder(source)
				.bind("lhamalog.openobserve", Bindable.of(OpenObserveProperties.class))
				.get();
		assertEquals("events", properties.logsStreamPrefix());
		assertEquals("events_acme", properties.streamForTenant("acme"));
		assertEquals("tenant_id", properties.tenantField());
		assertEquals("product_id", properties.productField());
	}
}
