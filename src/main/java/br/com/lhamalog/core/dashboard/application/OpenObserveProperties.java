package br.com.lhamalog.core.dashboard.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenObserve read settings for dashboard log counts. Credentials come only from env
 * ({@code OPENOBSERVE_AUTH}); nothing secret is defaulted here.
 *
 * @param baseUrl OpenObserve HTTP base (no trailing slash), e.g. http://localhost:5080
 * @param organization OpenObserve org (ADR_168: {@code lhamalog})
 * @param auth Authorization header value (typically {@code Basic ...}); blank disables real calls
 * @param streamTemplate Stream name template; {@code {tenantId}} is replaced
 * @param productField SQL field used to filter by product
 * @param severityField SQL field carrying the raw severity/level
 * @param serviceField SQL field carrying the service name
 * @param connectTimeout HTTP connect timeout
 * @param readTimeout HTTP read timeout
 */
@ConfigurationProperties("lhamalog.openobserve")
public record OpenObserveProperties(
		@DefaultValue("http://localhost:5080") String baseUrl,
		@DefaultValue("lhamalog") String organization,
		String auth,
		@DefaultValue("{tenantId}") String streamTemplate,
		@DefaultValue("product_id") String productField,
		@DefaultValue("level") String severityField,
		@DefaultValue("service_name") String serviceField,
		@DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout) {

	public OpenObserveProperties {
		baseUrl = stripTrailingSlash(baseUrl == null ? "http://localhost:5080" : baseUrl);
		organization = organization == null || organization.isBlank() ? "lhamalog" : organization.trim();
		streamTemplate = streamTemplate == null || streamTemplate.isBlank() ? "{tenantId}" : streamTemplate.trim();
		productField = blankTo(productField, "product_id");
		severityField = blankTo(severityField, "level");
		serviceField = blankTo(serviceField, "service_name");
	}

	public String streamForTenant(String tenantId) {
		return streamTemplate.replace("{tenantId}", tenantId == null ? "" : tenantId);
	}

	public boolean hasAuth() {
		return auth != null && !auth.isBlank();
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
