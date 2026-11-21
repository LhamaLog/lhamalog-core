package br.com.lhamalog.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import br.com.lhamalog.core.upload.CollectorManualUploadClient;
import br.com.lhamalog.core.upload.OutboundUploadFile;
import br.com.lhamalog.core.upload.RestCollectorManualUploadClient;

/** The bean factory uses a dedicated {@link RestClient#builder()}, not the auto-configured Boot builder (I1). */
class ManualUploadConfigurationTest {

	private HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void realBeanForwardsWithItsOwnRestClientBuilder() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext(RestCollectorManualUploadClient.PATH, (exchange) -> {
			byte[] body = """
					{"loteId":"lote-1","files":[{"name":"app.log","fileId":"f1","batchId":"b1","status":"ACCEPTED"}]}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.getResponseHeaders().add("Retry-After", "12");
			exchange.sendResponseHeaders(202, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		ManualUploadProperties properties = new ManualUploadProperties(List.of("log"),
				"http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(5),
				DataSize.ofMegabytes(100), DataSize.ofMegabytes(100));
		CollectorManualUploadClient client = new ManualUploadConfiguration()
			.collectorManualUploadClient(properties, "service-token");
		assertInstanceOf(RestCollectorManualUploadClient.class, client);
		byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
		CollectorManualUploadClient.Response response = client.forward(new CollectorManualUploadClient.Request("lote-1",
				"tenant-a", "product-b", "cid-1",
				List.of(new OutboundUploadFile("app.log", MediaType.TEXT_PLAIN, new ByteArrayResource(payload) {
					@Override
					public String getFilename() {
						return "app.log";
					}
				}, payload.length))));
		assertEquals(202, response.status());
		assertEquals("12", response.retryAfter());
		assertTrue(response.body().files().get(0).status().equals("ACCEPTED"));
	}
}
