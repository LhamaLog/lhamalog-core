package br.com.lhamalog.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.logging.CorrelationId;
import br.com.lhamalog.core.upload.CollectorManualUploadClient.Request;
import br.com.lhamalog.core.upload.CollectorManualUploadClient.Response;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;

class RestCollectorManualUploadClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void forwardsMultipartWithServiceTokenAndCorrelationIdAndWithoutAuthorization() throws Exception {
		Captured captured = start(202, """
				{"loteId":"lote-1","files":[{"name":"app.log","fileId":"f1","batchId":"b1","status":"ACCEPTED"}]}
				""");
		RestCollectorManualUploadClient client = client("  \"secret-token\"  ", Duration.ofSeconds(2));
		byte[] payload = "line-1\nline-2\n".repeat(8_000).getBytes(StandardCharsets.UTF_8);
		Response response = client.forward(new Request("lote-1", "tenant-a", "product-b", "cid-42",
				List.of(new OutboundUploadFile("app.log", MediaType.TEXT_PLAIN, new CountingResource(payload),
						payload.length))));
		assertEquals(202, response.status());
		assertEquals("lote-1", response.body().loteId());
		assertEquals("ACCEPTED", response.body().files().get(0).status());
		assertEquals("7", response.retryAfter());
		assertEquals("secret-token", captured.serviceToken);
		assertEquals("cid-42", captured.correlationId);
		assertNull(captured.authorization);
		assertTrue(captured.body.contains("tenant-a"));
		assertTrue(captured.body.contains("product-b"));
		assertTrue(captured.body.contains("lote-1"));
		assertTrue(captured.body.contains("app.log"));
		assertTrue(captured.body.contains("line-1"));
		assertEquals(1, CountingResource.streamsOpened.get());
	}

	@Test
	void mapsNonSuccessCollectorStatus() throws Exception {
		start(429, """
				{"loteId":"lote-1","files":[{"name":"app.log","status":"TOO_LARGE","reason":"quota"}]}
				""");
		Response response = client("token", Duration.ofSeconds(2))
			.forward(request("small.log", "hi".getBytes(StandardCharsets.UTF_8)));
		assertEquals(429, response.status());
		assertEquals("TOO_LARGE", response.body().files().get(0).status());
	}

	@Test
	void timesOutWhenTheCollectorDoesNotAnswer() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext(RestCollectorManualUploadClient.PATH, (exchange) -> {
			try {
				Thread.sleep(1_200);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			exchange.sendResponseHeaders(202, -1);
			exchange.close();
		});
		server.start();
		CollectorForwardException exception = assertThrows(CollectorForwardException.class,
				() -> client("token", Duration.ofMillis(200))
					.forward(request("app.log", "x".getBytes(StandardCharsets.UTF_8))));
		assertTrue(exception.timeout());
	}

	@Test
	void doesNotCallTheCollectorAndDoesNotFailOpenWhenTheUrlIsBlank() {
		CollectorForwardException exception = assertThrows(CollectorForwardException.class,
				() -> new RestCollectorManualUploadClient(RestClient.builder(),
						properties("  "), "service-token")
					.forward(request("app.log", "x".getBytes(StandardCharsets.UTF_8))));
		assertFalse(exception.timeout());
		assertTrue(exception.getMessage().toLowerCase().contains("url"));
	}

	@Test
	void doesNotCallTheCollectorWhenTheUrlHasNoHttpScheme() {
		CollectorForwardException exception = assertThrows(CollectorForwardException.class,
				() -> new RestCollectorManualUploadClient(RestClient.builder(),
						properties("lhamalog-collector:8080"), "service-token")
					.forward(request("app.log", "x".getBytes(StandardCharsets.UTF_8))));
		assertFalse(exception.timeout());
		assertTrue(exception.getMessage().toLowerCase().contains("url"));
	}

	@Test
	void capsHowManyBytesAreReadFromTheCollectorResponseBody() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(RestCollectorManualUploadClient.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			int cap = RestCollectorManualUploadClient.MAX_RESPONSE_BYTES;
			String prefix = "{\"loteId\":\"lote-1\",\"files\":[],\"pad\":\"";
			String suffix = "\"}";
			int pad = cap + 1 - prefix.getBytes(StandardCharsets.UTF_8).length
					- suffix.getBytes(StandardCharsets.UTF_8).length;
			byte[] oversized = (prefix + "a".repeat(pad) + suffix).getBytes(StandardCharsets.UTF_8);
			assertTrue(oversized.length > cap);
			start(202, oversized);
			Response response = client("token", Duration.ofSeconds(2))
				.forward(request("app.log", "ok".getBytes(StandardCharsets.UTF_8)));
			assertEquals(202, response.status());
			assertNull(response.body());
			assertTrue(appender.list.stream()
				.anyMatch((event) -> event.getFormattedMessage().contains("exceeded the read cap")));
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void doesNotCallTheCollectorWhenTheServiceTokenIsMissing() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext(RestCollectorManualUploadClient.PATH, (exchange) -> {
			hits.incrementAndGet();
			exchange.sendResponseHeaders(202, -1);
			exchange.close();
		});
		server.start();
		CollectorForwardException exception = assertThrows(CollectorForwardException.class,
				() -> client("  ", Duration.ofSeconds(1))
					.forward(request("app.log", "x".getBytes(StandardCharsets.UTF_8))));
		assertFalse(exception.timeout());
		assertEquals(0, hits.get());
	}

	private RestCollectorManualUploadClient client(String token, Duration timeout) {
		int port = server.getAddress().getPort();
		ManualUploadProperties properties = new ManualUploadProperties(List.of("log"),
				"http://127.0.0.1:" + port, Duration.ofSeconds(2), timeout, DataSize.ofMegabytes(100),
				DataSize.ofMegabytes(100));
		return new RestCollectorManualUploadClient(RestClient.builder(), properties, token);
	}

	private static ManualUploadProperties properties(String collectorUrl) {
		return new ManualUploadProperties(List.of("log"), collectorUrl, Duration.ofSeconds(2), Duration.ofSeconds(2),
				DataSize.ofMegabytes(100), DataSize.ofMegabytes(100));
	}

	private Request request(String name, byte[] payload) {
		return new Request("lote-1", "tenant-a", "product-b", "cid-42",
				List.of(new OutboundUploadFile(name, MediaType.TEXT_PLAIN, new CountingResource(payload), payload.length)));
	}

	private Captured start(int status, String json) throws IOException {
		return start(status, json.getBytes(StandardCharsets.UTF_8));
	}

	private Captured start(int status, byte[] response) throws IOException {
		Captured captured = new Captured();
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext(RestCollectorManualUploadClient.PATH, (exchange) -> {
			captured.serviceToken = exchange.getRequestHeaders().getFirst(RestCollectorManualUploadClient.SERVICE_TOKEN_HEADER);
			captured.correlationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
			captured.authorization = exchange.getRequestHeaders().getFirst("Authorization");
			captured.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.getResponseHeaders().add("Retry-After", "7");
			exchange.sendResponseHeaders(status, response.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(response);
			}
		});
		server.start();
		return captured;
	}

	private static final class Captured {
		private String serviceToken;
		private String correlationId;
		private String authorization;
		private String body;
	}

	private static final class CountingResource extends InputStreamResource {

		private static final AtomicInteger streamsOpened = new AtomicInteger();

		private final byte[] payload;

		private CountingResource(byte[] payload) {
			super(InputStream.nullInputStream(), "app.log");
			this.payload = payload;
			streamsOpened.set(0);
		}

		@Override
		public String getFilename() {
			return "app.log";
		}

		@Override
		public long contentLength() {
			return payload.length;
		}

		@Override
		public InputStream getInputStream() {
			streamsOpened.incrementAndGet();
			return new java.io.ByteArrayInputStream(payload);
		}
	}
}
