package br.com.lhamalog.core.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import br.com.lhamalog.core.upload.RestCollectorManualUploadClient;

/** Startup warnings for the manual-upload Collector URL and service token (I4, N1). */
public final class ManualUploadStartupChecks {

	private ManualUploadStartupChecks() {
	}

	public static List<String> warnings(boolean localProfile, String serviceToken, String collectorUrl, int serverPort) {
		List<String> warnings = new ArrayList<>();
		String token = RestCollectorManualUploadClient.normalizeToken(serviceToken);
		if (!localProfile && (token == null || token.isEmpty())) {
			warnings.add(
					"event=manual_upload.collector_token_missing | LHAMALOG_CORE_COLLECTOR_TOKEN is empty outside the local profile; POST /api/manual/upload will fail with 503 until it is set");
		}
		String url = collectorUrl == null ? "" : collectorUrl.strip();
		if (url.isEmpty()) {
			warnings.add(
					"event=manual_upload.collector_url_missing | lhamalog.manual-upload.collector-url is blank; a defined-but-empty LHAMALOG_MANUAL_UPLOAD_COLLECTOR_URL does not fall back to http://localhost:8080, and POST /api/manual/upload will fail with 503 until the Collector URL is set");
		}
		else if (!ManualUploadProperties.isUsableCollectorUrl(url)) {
			warnings.add(
					"event=manual_upload.collector_url_invalid | lhamalog.manual-upload.collector-url must start with http:// or https:// (a host:port value is not used as a base URL); POST /api/manual/upload will fail with 503 until a valid Collector URL is set");
		}
		else if (looksLikeCorePort(url, serverPort)) {
			warnings.add(
					"event=manual_upload.collector_url_self | lhamalog.manual-upload.collector-url looks like this Core's own listen port; set LHAMALOG_MANUAL_UPLOAD_COLLECTOR_URL to the Collector or uploads are forwarded to the Core itself");
		}
		return List.copyOf(warnings);
	}

	static boolean looksLikeCorePort(String collectorUrl, int serverPort) {
		if (collectorUrl == null || collectorUrl.isBlank()) {
			return false;
		}
		try {
			URI uri = URI.create(collectorUrl.strip());
			String host = uri.getHost();
			if (host == null) {
				return false;
			}
			boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
					|| "0.0.0.0".equals(host) || "::1".equals(host);
			int port = uri.getPort();
			if (port < 0) {
				port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
			}
			return loopback && port == serverPort;
		}
		catch (RuntimeException ignored) {
			return false;
		}
	}
}
