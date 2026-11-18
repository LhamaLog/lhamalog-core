package br.com.lhamalog.core.identity.adapter.out.clerk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Adapter test against a fake Clerk Backend API served by the JDK HTTP server (no mock library). */
class ClerkBackendSessionRevocationTests {

	private static final String SECRET = "test-secret-not-real";

	private final List<String> requests = new CopyOnWriteArrayList<>();

	private final List<String> authorizations = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private volatile String listBody = "[]";

	private volatile int listStatus = 200;

	private volatile int revokeStatus = 200;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/v1/sessions", this::handle);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void listsActiveSessionsAndRevokesEachOne() {
		listBody = "[{\"id\":\"sess_1\",\"status\":\"active\"},{\"id\":\"sess_2\",\"status\":\"active\"}]";

		revocation(SECRET).revokeAllActiveSessions("user_42");

		assertThat(requests).containsExactly("GET /v1/sessions?user_id=user_42&status=active&limit=500",
				"POST /v1/sessions/sess_1/revoke", "POST /v1/sessions/sess_2/revoke");
		assertThat(authorizations).containsOnly("Bearer " + SECRET);
	}

	@Test
	void acceptsPaginatedResponseShape() {
		listBody = "{\"data\":[{\"id\":\"sess_9\"}],\"total_count\":1}";

		revocation(SECRET).revokeAllActiveSessions("user_42");

		assertThat(requests).contains("POST /v1/sessions/sess_9/revoke");
	}

	@Test
	void noActiveSessionMeansNoRevokeCall() {
		revocation(SECRET).revokeAllActiveSessions("user_42");

		assertThat(requests).hasSize(1);
	}

	@Test
	void listFailureIsReportedToCaller() {
		listStatus = 500;

		assertThatThrownBy(() -> revocation(SECRET).revokeAllActiveSessions("user_42"))
			.isInstanceOf(RestClientResponseException.class);
	}

	@Test
	void revokeFailureIsReportedToCaller() {
		listBody = "[{\"id\":\"sess_1\"}]";
		revokeStatus = 404;

		assertThatThrownBy(() -> revocation(SECRET).revokeAllActiveSessions("user_42"))
			.isInstanceOf(RestClientResponseException.class);
	}

	@Test
	void missingSecretKeySkipsTheCall() {
		revocation("").revokeAllActiveSessions("user_42");
		revocation(null).revokeAllActiveSessions("user_42");

		assertThat(requests).isEmpty();
	}

	private ClerkBackendSessionRevocation revocation(String secretKey) {
		return new ClerkBackendSessionRevocation(RestClient.builder(),
				"http://127.0.0.1:" + server.getAddress().getPort(), secretKey, Duration.ofSeconds(2),
				Duration.ofSeconds(2));
	}

	private void handle(HttpExchange exchange) throws IOException {
		String query = exchange.getRequestURI().getRawQuery();
		requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
				+ ((query != null) ? "?" + query : ""));
		authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
		boolean revoke = exchange.getRequestURI().getPath().endsWith("/revoke");
		int status = revoke ? revokeStatus : listStatus;
		byte[] body = (revoke ? "{\"status\":\"revoked\"}" : listBody).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

}
