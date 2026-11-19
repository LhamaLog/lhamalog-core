package br.com.lhamalog.core.dashboard.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenObserve read settings for dashboard log counts. Credentials come only from env
 * ({@code OPENOBSERVE_AUTH}); nothing secret is defaulted here.
 *
 * <p>Field defaults match OpenObserve lowercase normalization of Collector documents
 * ({@code tenantid}, {@code productid}, {@code severitynumber}, …). Stream names follow
 * {@link OpenObserveStreamNames} with prefix {@code OPENOBSERVE_LOGS_STREAM} (default {@code logs}).
 *
 * @param baseUrl OpenObserve HTTP base (no trailing slash), e.g. http://localhost:5080
 * @param organization OpenObserve org (ADR_168: {@code lhamalog})
 * @param auth Authorization header value (typically {@code Basic ...}); blank disables real calls
 * @param logsStreamPrefix Stream prefix (Collector {@code OPENOBSERVE_LOGS_STREAM})
 * @param tenantField SQL field for tenant id (defense-in-depth filter)
 * @param productField SQL field used to filter by product
 * @param severityNumberField SQL field for OTel severity number (1–24)
 * @param severityTextField SQL field for free-text severity (fallback)
 * @param serviceField SQL field carrying the service name
 * @param connectTimeout HTTP connect timeout
 * @param readTimeout HTTP read timeout
 */
@ConfigurationProperties("lhamalog.openobserve")
public record OpenObserveProperties(
		@DefaultValue("http://localhost:5080") String baseUrl,
		@DefaultValue("lhamalog") String organization,
		String auth,
		@DefaultValue("logs") String logsStreamPrefix,
		@DefaultValue("tenantid") String tenantField,
		@DefaultValue("productid") String productField,
		@DefaultValue("severitynumber") String severityNumberField,
		@DefaultValue("severitytext") String severityTextField,
		@DefaultValue("servicename") String serviceField,
		@DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout) {

	public OpenObserveProperties {
		baseUrl = stripTrailingSlash(baseUrl == null ? "http://localhost:5080" : baseUrl);
		organization = organization == null || organization.isBlank() ? "lhamalog" : organization.trim();
		logsStreamPrefix = blankTo(logsStreamPrefix, "logs");
		tenantField = blankTo(tenantField, "tenantid");
		productField = blankTo(productField, "productid");
		severityNumberField = blankTo(severityNumberField, "severitynumber");
		severityTextField = blankTo(severityTextField, "severitytext");
		serviceField = blankTo(serviceField, "servicename");
	}

	public String streamForTenant(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenantId is required for stream name");
		}
		return OpenObserveStreamNames.resolve(logsStreamPrefix, tenantId);
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
