package br.com.lhamalog.core.upload;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.logging.CorrelationId;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for {@code POST /internal/manual/upload}. The file parts are sent as resources so the body can be
 * streamed instead of fully buffered again.
 */
public class RestCollectorManualUploadClient implements CollectorManualUploadClient {

	public static final String SERVICE_TOKEN_HEADER = "X-LhamaLog-Service-Token";

	public static final String PATH = "/internal/manual/upload";

	/** Upper bound when reading the Collector's JSON body (the payload is a small decision document). */
	static final int MAX_RESPONSE_BYTES = 64 * 1024;

	private static final Logger log = LoggerFactory.getLogger(RestCollectorManualUploadClient.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient restClient;

	private final String collectorUrl;

	private final String serviceToken;

	public RestCollectorManualUploadClient(RestClient.Builder builder, ManualUploadProperties properties,
			String serviceToken) {
		// HTTP/1.1 is pinned for the http:// forward of large multipart bodies. The JDK prefers HTTP/2
		// and the h2c upgrade path has not been verified for this upload.
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(properties.connectTimeoutOrDefault())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeoutOrDefault());
		this.collectorUrl = properties.normalizedCollectorUrl();
		RestClient.Builder configured = builder.clone().requestFactory(requestFactory);
		if (ManualUploadProperties.isUsableCollectorUrl(this.collectorUrl)) {
			configured.baseUrl(this.collectorUrl);
		}
		this.restClient = configured.build();
		this.serviceToken = normalizeToken(serviceToken);
	}

	@Override
	public Response forward(Request request) {
		if (serviceToken == null || serviceToken.isEmpty()) {
			log.warn(
					"event=manual_upload.collector_token_missing | Collector service token is not configured; upload is not forwarded | productId={}",
					request.productId());
			throw new CollectorForwardException("Collector service token is not configured", false, null);
		}
		if (!ManualUploadProperties.isUsableCollectorUrl(collectorUrl)) {
			if (collectorUrl.isEmpty()) {
				log.warn(
						"event=manual_upload.collector_url_missing | Collector URL is not configured; upload is not forwarded | productId={}",
						request.productId());
			}
			else {
				log.warn(
						"event=manual_upload.collector_url_invalid | Collector URL is missing an http(s) scheme or is malformed; upload is not forwarded | productId={}",
						request.productId());
			}
			throw new CollectorForwardException("Collector URL is not configured", false, null);
		}
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("tenantId", request.tenantId());
		body.part("productId", request.productId());
		body.part("loteId", request.loteId());
		for (OutboundUploadFile file : request.files()) {
			MediaType contentType = file.contentType() != null ? file.contentType()
					: MediaType.APPLICATION_OCTET_STREAM;
			body.part("files", file.resource()).filename(file.filename()).contentType(contentType);
		}
		try {
			return restClient.post()
				.uri(PATH)
				.header(SERVICE_TOKEN_HEADER, serviceToken)
				.header(CorrelationId.HEADER, request.correlationId())
				.body(body.build())
				.exchange((httpRequest, httpResponse) -> new Response(httpResponse.getStatusCode().value(),
						readBody(httpResponse), forwardedHeaders(httpResponse.getHeaders())));
		}
		catch (RestClientException exception) {
			throw wrap(exception);
		}
		catch (IllegalArgumentException exception) {
			throw new CollectorForwardException("Collector URL is invalid", false, exception);
		}
	}

	public static String normalizeToken(String token) {
		if (token == null) {
			return null;
		}
		String value = token.strip();
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if (first == last && (first == '"' || first == '\'')) {
				value = value.substring(1, value.length() - 1).strip();
			}
		}
		return value;
	}

	static HttpHeaders forwardedHeaders(HttpHeaders source) {
		HttpHeaders headers = new HttpHeaders();
		if (source == null) {
			return headers;
		}
		List<String> retryAfter = source.get(HttpHeaders.RETRY_AFTER);
		if (retryAfter != null && !retryAfter.isEmpty()) {
			headers.put(HttpHeaders.RETRY_AFTER, List.copyOf(retryAfter));
		}
		return headers;
	}

	private static Body readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
			throws IOException {
		try (InputStream input = response.getBody()) {
			if (input == null) {
				return null;
			}
			byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
			if (bytes.length > MAX_RESPONSE_BYTES) {
				log.warn(
						"event=manual_upload.collector_body_unreadable | Collector response body exceeded the read cap | status={} cap={}",
						response.getStatusCode().value(), MAX_RESPONSE_BYTES);
				return null;
			}
			if (bytes.length == 0) {
				return null;
			}
			return JSON.readValue(bytes, Body.class);
		}
		catch (RuntimeException exception) {
			log.warn(
					"event=manual_upload.collector_body_unreadable | Could not parse Collector response body | status={}",
					response.getStatusCode().value());
			return null;
		}
	}

	private static CollectorForwardException wrap(RuntimeException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException
					|| cause instanceof java.net.SocketTimeoutException) {
				return new CollectorForwardException("Collector request timed out", true, exception);
			}
			cause = cause.getCause();
		}
		return new CollectorForwardException("Collector is unavailable", false, exception);
	}
}
