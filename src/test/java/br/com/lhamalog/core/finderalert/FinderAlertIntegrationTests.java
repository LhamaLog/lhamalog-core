package br.com.lhamalog.core.finderalert;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.mongodb.client.model.Filters;

import br.com.lhamalog.core.TestcontainersConfiguration;
import br.com.lhamalog.core.occurrence.InternalModuleWorld;
import br.com.lhamalog.core.occurrence.InternalTokens;
import br.com.lhamalog.core.support.ClerkTestConfiguration;
import br.com.lhamalog.core.support.TestJwks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Spec 016, US1: one alert in {@code finder_alerts}, 201 then 200, and {@code FINDER_ALERT} only on the first write.
 * Needs Docker (Testcontainers). Not executed in the environment that has no Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ClerkTestConfiguration.class })
class FinderAlertIntegrationTests {

	private static final String PATH = "/internal/v1/alertas/finder";

	private static final String MOTIVO = FinderAlertCommand.OPENOBSERVE_UNAVAILABLE;

	private static final String NOTIFICATIONS = "lhamalog:notifications:events";

	@LocalServerPort int port;
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	@Autowired StringRedisTemplate redis;

	@Test
	void firstWriteThenRepeatKeepsOneAlertAndNotifiesOnce() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		Client admin = ready(company.organization(), "admin_alert", "admin");
		Client outsider = ready(company.organization(), "member_" + UUID.randomUUID(), "member");
		String batchId = "batch-" + UUID.randomUUID();
		long noticesBefore = streamSize();
		String authorization = bearer(bothAudiences());

		JsonNode created = post(company, batchId, authorization, 201);
		assertBody(created, company, batchId);
		assertThat(count(company, batchId)).isEqualTo(1);

		JsonNode notice = mapper.readTree(next(admin));
		assertThat(notice.get("type").asString()).isEqualTo("FINDER_ALERT");
		assertThat(notice.get("tenantId").asString()).isEqualTo(company.tenantId());
		assertThat(notice.get("productId").asString()).isEqualTo(company.productId());
		assertThat(notice.get("batchId").asString()).isEqualTo(batchId);
		assertThat(notice.get("motivo").asString()).isEqualTo(MOTIVO);
		assertThat(notice.size()).isEqualTo(5);
		assertThat(admin.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
		assertThat(outsider.messages).isEmpty();
		assertThat(streamSize()).isEqualTo(noticesBefore);

		JsonNode repeated = post(company, batchId, authorization, 200);
		assertThat(repeated).isEqualTo(created);
		assertThat(count(company, batchId)).isEqualTo(1);
		assertThat(admin.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
		assertThat(streamSize()).isEqualTo(noticesBefore);
	}

	@Test
	void twoFirstWritesLeaveOneDocument() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		String batchId = "race-" + UUID.randomUUID();
		String body = mapper.writeValueAsString(body(company, batchId));
		HttpRequest request = request(body, bearer(bothAudiences()));
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		var left = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		var right = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> first = left.get(2, TimeUnit.SECONDS);
		HttpResponse<String> second = right.get(2, TimeUnit.SECONDS);
		assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(201, 200);
		assertThat(count(company, batchId)).isEqualTo(1);
		JsonNode winner = mapper.readTree(first.statusCode() == 201 ? first.body() : second.body());
		JsonNode loser = mapper.readTree(first.statusCode() == 200 ? first.body() : second.body());
		assertBody(winner, company, batchId);
		assertThat(loser).isEqualTo(winner);
	}

	@Test
	void invalidBodiesAndAForeignProductDoNotStore() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		InternalModuleWorld.Company other = world.company();
		String authorization = bearer(bothAudiences());
		String batchId = "invalid-" + UUID.randomUUID();

		assertCode("nao-json", authorization, 400, "REQUISICAO_INVALIDA");
		assertCode(mapper.writeValueAsString(Map.of("tenantId", company.tenantId(), "productId", company.productId(),
				"batchId", batchId)), authorization, 422, "DADOS_INVALIDOS");
		Map<String, Object> extra = body(company, batchId);
		extra.put("repeticao", false);
		assertCode(mapper.writeValueAsString(extra), authorization, 422, "DADOS_INVALIDOS");
		Map<String, Object> wrong = body(company, batchId);
		wrong.put("motivo", "outro");
		assertCode(mapper.writeValueAsString(wrong), authorization, 422, "DADOS_INVALIDOS");
		Map<String, Object> foreign = body(company, batchId);
		foreign.put("productId", other.productId());
		assertCode(mapper.writeValueAsString(foreign), authorization, 403, "ACESSO_NEGADO");
		assertThat(mongo.getCollection(FinderAlertDocument.COLLECTION).countDocuments(Filters.eq("batchId", batchId)))
			.isZero();
	}

	private void assertBody(JsonNode body, InternalModuleWorld.Company company, String batchId) {
		assertThat(body.get("aceito").asBoolean()).isTrue();
		assertThat(body.get("tenantId").asString()).isEqualTo(company.tenantId());
		assertThat(body.get("productId").asString()).isEqualTo(company.productId());
		assertThat(body.get("batchId").asString()).isEqualTo(batchId);
		assertThat(body.get("motivo").asString()).isEqualTo(MOTIVO);
		assertThat(body.size()).isEqualTo(5);
		assertThat(body.has("repeticao")).isFalse();
	}

	private void assertCode(String body, String authorization, int status, String code) throws Exception {
		HttpResponse<String> response = send(body, authorization);
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(mapper.readTree(response.body()).get("codigo").asString()).isEqualTo(code);
		assertThat(response.body()).doesNotContain("repeticao");
	}

	private JsonNode post(InternalModuleWorld.Company company, String batchId, String authorization, int expected)
			throws Exception {
		long started = System.nanoTime();
		HttpResponse<String> response = send(mapper.writeValueAsString(body(company, batchId)), authorization);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
		assertThat(response.statusCode()).isEqualTo(expected);
		return mapper.readTree(response.body());
	}

	private HttpResponse<String> send(String body, String authorization) throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		return http.send(request(body, authorization), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String body, String authorization) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
			.timeout(Duration.ofSeconds(2))
			.header("Authorization", authorization)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
	}

	private static Map<String, Object> body(InternalModuleWorld.Company company, String batchId) {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("tenantId", company.tenantId());
		body.put("productId", company.productId());
		body.put("batchId", batchId);
		body.put("motivo", MOTIVO);
		return body;
	}

	private long count(InternalModuleWorld.Company company, String batchId) {
		return mongo.getCollection(FinderAlertDocument.COLLECTION).countDocuments(Filters.and(
				Filters.eq("tenantId", company.tenantId()), Filters.eq("productId", company.productId()),
				Filters.eq("batchId", batchId), Filters.eq("motivo", MOTIVO)));
	}

	private long streamSize() {
		Long size = redis.opsForStream().size(NOTIFICATIONS);
		return size == null ? 0 : size;
	}

	private static String bothAudiences() {
		return InternalTokens.finder().claim("aud", List.of("lhamalog-modulo-ocorrencia", "lhamalog-core")).sign();
	}

	private static String bearer(String jwt) {
		return "Bearer " + jwt;
	}

	private Client ready(String organization, String user, String role) throws Exception {
		Client client = new Client();
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin("http://localhost:5173");
		new StandardWebSocketClient()
			.execute(client, headers, URI.create("ws://localhost:" + port + "/ws/occurrences"))
			.get(5, TimeUnit.SECONDS);
		client.session.sendMessage(new TextMessage("{\"type\":\"auth\",\"token\":\""
				+ TestJwks.token().organization(organization, role).subject(user).encode() + "\"}"));
		assertThat(mapper.readTree(next(client)).get("type").asString()).isEqualTo("ready");
		return client;
	}

	private String next(Client client) throws Exception {
		String message = client.messages.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		return message;
	}

	private static final class Client extends TextWebSocketHandler {
		final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
		final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
		WebSocketSession session;

		@Override
		public void afterConnectionEstablished(WebSocketSession session) {
			this.session = session;
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			messages.add(message.getPayload());
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
			closed.complete(status);
		}
	}

}
