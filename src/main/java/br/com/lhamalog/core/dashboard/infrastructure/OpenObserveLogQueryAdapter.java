package br.com.lhamalog.core.dashboard.infrastructure;

import br.com.lhamalog.core.dashboard.application.LogCountBucket;
import br.com.lhamalog.core.dashboard.application.LogSeverity;
import br.com.lhamalog.core.dashboard.application.OpenObserveLogQueryPort;
import br.com.lhamalog.core.dashboard.application.OpenObserveProperties;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * OpenObserve search adapter: {@code POST /api/{org}/_search} with SQL count aggregation.
 */
@Component
public class OpenObserveLogQueryAdapter implements OpenObserveLogQueryPort {

	private static final Logger log = LoggerFactory.getLogger(OpenObserveLogQueryAdapter.class);

	private final RestClient restClient;
	private final OpenObserveProperties properties;

	public OpenObserveLogQueryAdapter(OpenObserveProperties properties) {
		this.properties = properties;
		// Plain RestClient.builder() — Boot's restclient module is not on the classpath (same as Clerk).
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	@Override
	public List<LogCountBucket> countLogs(String tenantId, String productId, Instant from, Instant to,
			Set<LogSeverity> severities) {
		if (!from.isBefore(to)) {
			return List.of();
		}
		if (!properties.hasAuth()) {
			log.warn(
					"event=dashboard.openobserve.unconfigured | OPENOBSERVE_AUTH missing; log search skipped | tenantId={} productId={}",
					tenantId, productId);
			return List.of();
		}
		Set<LogSeverity> filter = severities == null || severities.isEmpty()
				? EnumSet.allOf(LogSeverity.class)
				: EnumSet.copyOf(severities);
		String stream = properties.streamForTenant(tenantId);
		String sql = buildSql(stream, productId);
		Map<String, Object> body = Map.of(
				"query", Map.of(
						"sql", sql,
						"start_time", from.toEpochMilli() * 1_000L,
						"end_time", to.toEpochMilli() * 1_000L,
						"from", 0,
						"size", 10_000));
		try {
			JsonNode response = restClient.post()
					.uri("/api/{org}/_search", properties.organization())
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						if (properties.hasAuth()) {
							headers.set(HttpHeaders.AUTHORIZATION, properties.auth());
						}
					})
					.body(body)
					.retrieve()
					.body(JsonNode.class);
			return parseHits(response, filter);
		}
		catch (RuntimeException exception) {
			log.error(
					"event=dashboard.openobserve.failed | OpenObserve log search failed | tenantId={} productId={} from={} to={}",
					tenantId, productId, from, to);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Falha ao consultar métricas de logs. Tente novamente.", exception);
		}
	}

	private String buildSql(String stream, String productId) {
		String escapedStream = stream.replace("\"", "");
		String escapedProduct = productId.replace("'", "''");
		return "SELECT histogram(_timestamp, '1 hour') AS hour_start, "
				+ properties.severityField() + " AS severity, "
				+ properties.serviceField() + " AS service_name, "
				+ "count(*) AS cnt FROM \"" + escapedStream + "\" WHERE "
				+ properties.productField() + " = '" + escapedProduct + "' "
				+ "GROUP BY hour_start, severity, service_name ORDER BY hour_start";
	}

	public static List<LogCountBucket> parseHits(JsonNode response, Set<LogSeverity> filter) {
		List<LogCountBucket> buckets = new ArrayList<>();
		if (response == null) {
			return buckets;
		}
		JsonNode hits = response.get("hits");
		if (hits == null || !hits.isArray()) {
			return buckets;
		}
		for (JsonNode hit : hits) {
			Instant hourStart = readHour(hit);
			LogSeverity severity = LogSeverity.fromRaw(text(hit, "severity"))
					.or(() -> LogSeverity.fromRaw(text(hit, "level")))
					.orElse(null);
			if (hourStart == null || severity == null || !filter.contains(severity)) {
				continue;
			}
			String service = text(hit, "service_name");
			if (service == null || service.isBlank()) {
				service = text(hit, "serviceName");
			}
			long count = readCount(hit);
			if (count <= 0) {
				continue;
			}
			buckets.add(new LogCountBucket(hourStart, severity, service, count));
		}
		return buckets;
	}

	private static Instant readHour(JsonNode hit) {
		JsonNode node = hit.get("hour_start");
		if (node == null) {
			node = hit.get("zo_sql_key");
		}
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isNumber()) {
			long value = node.asLong();
			// OpenObserve may return µs or ms epoch; normalize to Instant truncated to hour.
			Instant instant = value > 10_000_000_000_000L
					? Instant.ofEpochMilli(value / 1_000L)
					: Instant.ofEpochMilli(value);
			return instant.truncatedTo(ChronoUnit.HOURS);
		}
		String text = node.asString();
		try {
			return Instant.parse(text).truncatedTo(ChronoUnit.HOURS);
		}
		catch (RuntimeException ignored) {
			return null;
		}
	}

	private static long readCount(JsonNode hit) {
		JsonNode node = hit.get("cnt");
		if (node == null) {
			node = hit.get("zo_sql_num");
		}
		if (node == null || !node.isNumber()) {
			return 0L;
		}
		return node.asLong();
	}

	private static String text(JsonNode hit, String field) {
		JsonNode node = hit.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		return node.asString();
	}
}
