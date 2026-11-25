package br.com.lhamalog.core.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import br.com.lhamalog.core.shared.UploadRequestSizeLabel;

/**
 * Configuration of the manual upload path (LLT-292). Size limits are also set on the servlet/Tomcat properties;
 * those pairs must stay in sync with {@code maxFileSize} and {@code maxRequestSize}, which are what the validator
 * and 413 messages use. The Collector service token is not a property: it comes only from
 * {@code LHAMALOG_CORE_COLLECTOR_TOKEN}.
 *
 * <p>A Collector URL that is missing uses the {@code @DefaultValue} ({@code http://localhost:8080}). A URL that is
 * present but blank (Compose and {@code .env.example} define the env var empty) is kept blank: the Spring default
 * does not apply, and the client treats it as unset (503). A value without an {@code http} or {@code https} scheme
 * (for example {@code lhamalog-collector:8080}) is also treated as unusable (WARN and 503).
 */
@ConfigurationProperties(prefix = "lhamalog.manual-upload")
public record ManualUploadProperties(
		@DefaultValue("txt,json,csv,log") List<String> allowedExtensions,
		@DefaultValue("http://localhost:8080") String collectorUrl,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("120s") Duration timeout,
		@DefaultValue("100MB") DataSize maxFileSize,
		@DefaultValue("100MB") DataSize maxRequestSize) implements UploadRequestSizeLabel {

	public ManualUploadProperties {
		collectorUrl = collectorUrl == null ? "" : collectorUrl.strip();
	}

	/** Collector base URL with surrounding whitespace removed; blank means unset. */
	public String normalizedCollectorUrl() {
		return collectorUrl;
	}

	/**
	 * Whether {@code url} can be used as a {@code RestClient} base URL: non-blank with an {@code http}
	 * or {@code https} scheme. Host:port values without a scheme are rejected so they do not become a 500.
	 */
	public static boolean isUsableCollectorUrl(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		}
		catch (RuntimeException ignored) {
			return false;
		}
	}

	public Set<String> extensionSet() {
		List<String> values = allowedExtensions == null ? List.of() : allowedExtensions;
		return values.stream().map(ManualUploadProperties::normalizeExtension).collect(Collectors.toUnmodifiableSet());
	}

	public long maxFileBytes() {
		return maxFileSize.toBytes();
	}

	public long maxRequestBytes() {
		return maxRequestSize.toBytes();
	}

	public Duration connectTimeoutOrDefault() {
		return connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
	}

	public Duration timeoutOrDefault() {
		return timeout == null ? Duration.ofSeconds(120) : timeout;
	}

	/** Human-readable size used in 413 messages, derived from {@link #maxRequestSize()}. */
	@Override
	public String maxRequestSizeLabel() {
		return label(maxRequestSize);
	}

	static String label(DataSize size) {
		if (size == null) {
			return "100 MB";
		}
		long bytes = size.toBytes();
		if (bytes > 0 && bytes % (1024 * 1024) == 0) {
			return (bytes / (1024 * 1024)) + " MB";
		}
		if (bytes > 0 && bytes % 1024 == 0) {
			return (bytes / 1024) + " KB";
		}
		return bytes + " B";
	}

	static String normalizeExtension(String raw) {
		String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
		return value.startsWith(".") ? value.substring(1) : value;
	}
}
