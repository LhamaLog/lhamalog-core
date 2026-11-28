package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.mongodb.client.model.Filters;

import br.com.lhamalog.core.TestcontainersConfiguration;
import br.com.lhamalog.core.support.ClerkTestConfiguration;
import br.com.lhamalog.core.support.TestJwks;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Spec 015 (LLT-137): the occurrence WebSocket, end to end with Redis and the Finder routes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "lhamalog.occurrence.stream.auth-timeout=2s",
				"lhamalog.occurrence.stream.maintenance-interval=200ms" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ClerkTestConfiguration.class })
class OccurrenceStreamIntegrationTests {

	private static final String ORIGIN = "http://localhost:5173";

	@LocalServerPort int port;
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	/** Messages and close status seen by one client connection. */
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

		void send(String text) throws Exception {
			session.sendMessage(new TextMessage(text));
		}
	}

	private Client connect() throws Exception {
		return connect(ORIGIN);
	}

	private Client connect(String origin) throws Exception {
		Client client = new Client();
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin(origin);
		new StandardWebSocketClient().execute(client, headers, URI.create("ws://localhost:" + port + "/ws/occurrences"))
			.get(5, TimeUnit.SECONDS);
		return client;
	}

	private JsonNode next(Client client) throws Exception {
		String message = client.messages.poll(5, TimeUnit.SECONDS);
		assertThat(message).as("message from the Core").isNotNull();
		return mapper.readTree(message);
	}

	private static String token(String organization, String user, String role) {
		return TestJwks.token().organization(organization, role).subject(user).encode();
	}

	private Client ready(String organization, String user, String role) throws Exception {
		Client client = connect();
		client.send("{\"type\":\"auth\",\"token\":\"" + token(organization, user, role) + "\"}");
		assertThat(next(client).get("type").asString()).isEqualTo("ready");
		return client;
	}

	private int closeCode(Client client) throws Exception {
		return client.closed.get(5, TimeUnit.SECONDS).getCode();
	}

	// US2

	@Test
	void connectionWithoutAuthIsClosedWith4401() throws Exception {
		assertThat(closeCode(connect())).isEqualTo(4401);
		Client wrong = connect();
		wrong.send("{\"type\":\"ping\"}");
		assertThat(closeCode(wrong)).isEqualTo(4401);
		Client invalid = connect();
		invalid.send("{\"type\":\"auth\",\"token\":\"nao-e-um-token\"}");
		assertThat(closeCode(invalid)).isEqualTo(4401);
		assertThat(invalid.messages).isEmpty();
	}

	@Test
	void validTokenWithoutAccessIsClosedWith4403() throws Exception {
		Client noTenant = connect();
		noTenant.send("{\"type\":\"auth\",\"token\":\"" + token("org_sem_tenant_" + UUID.randomUUID(), "user_x", "admin") + "\"}");
		assertThat(closeCode(noTenant)).isEqualTo(4403);
		Client noOrganization = connect();
		noOrganization.send("{\"type\":\"auth\",\"token\":\"" + TestJwks.token().without("o").subject("user_y").encode() + "\"}");
		assertThat(closeCode(noOrganization)).isEqualTo(4403);
	}

	@Test
	void validTokenIsReadyAndCanBeRenewedOnlyBySameIdentity() throws Exception {
		String organization = "org_test_" + UUID.randomUUID();
		new TestTenants(mongo).tenantId(organization);
		Client client = connect();
		client.send("{\"type\":\"auth\",\"token\":\"" + token(organization, "user_a", "admin") + "\"}");
		JsonNode ready = next(client);
		assertThat(ready.get("type").asString()).isEqualTo("ready");
		assertThat(ready.get("expiresAt").asString()).isNotBlank();
		client.send("{\"type\":\"auth\",\"token\":\"" + token(organization, "user_a", "admin") + "\"}");
		assertThat(next(client).get("type").asString()).isEqualTo("ready");
		client.send("{\"type\":\"ping\"}");
		client.send("{\"type\":\"auth\",\"token\":\"" + token(organization, "user_b", "admin") + "\"}");
		assertThat(closeCode(client)).isEqualTo(4403);
	}

	@Test
	void handshakeFromAnotherOriginIsRefused() {
		assertThatThrownBy(() -> connect("https://outro-site.example")).isInstanceOf(Exception.class);
	}

	// US1

	@Test
	void noticesReachOnlyWhoSeesTheProduct() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		Client admin = ready(company.organization(), "admin_stream", "admin");
		Client outsider = ready(company.organization(), "member_" + UUID.randomUUID(), "member");
		InternalModuleWorld.Company other = world.company();
		Client otherCompany = ready(other.organization(), "admin_other", "admin");

		String created = InternalModuleWorld.signature("nova " + UUID.randomUUID());
		String reopened = InternalModuleWorld.signature("reaberta " + UUID.randomUUID());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(reopened)).build())
			.andExpect(status().isCreated());
		JsonNode first = next(admin);
		assertThat(first.get("type").asString()).isEqualTo("occurrence");
		assertThat(first.get("change").asString()).isEqualTo("CREATED");
		assertThat(first.get("productId").asString()).isEqualTo(company.productId());
		assertThat(first.get("occurrenceType").asString()).isEqualTo("ERROR");
		assertThat(first.get("gravidade").asString()).isEqualTo("ERROR");
		assertThat(first.toString()).doesNotContain("reaberta").doesNotContain("tenantId");

		mongo.getCollection("Ocorrencia").updateOne(Filters.eq("signature", reopened),
				new Document("$set", new Document("status", "FECHADA")));
		String revision = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, reopened, "ERROR"))).get("estado").get("revisao").asString();
		var body = InternalModuleWorld.summary(company)
			.finding(InternalModuleWorld.finding(reopened).count(5).revision(revision))
			.finding(InternalModuleWorld.finding(created))
			.build();
		world.call(InternalModuleWorld.DELIVERY, body).andExpect(status().isCreated());
		String a = next(admin).get("change").asString();
		String b = next(admin).get("change").asString();
		assertThat(java.util.List.of(a, b)).containsExactlyInAnyOrder("REOPENED", "CREATED");

		// Repeated delivery and count going up on an open occurrence: no notice.
		world.call(InternalModuleWorld.DELIVERY, body).andExpect(status().isOk());
		String now = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, created, "ERROR"))).get("estado").get("revisao").asString();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(created).count(9).revision(now)).build())
			.andExpect(status().isCreated());
		assertThat(admin.messages.poll(2, TimeUnit.SECONDS)).isNull();
		assertThat(outsider.messages).isEmpty();
		assertThat(otherCompany.messages).isEmpty();
	}

	@Test
	void memberWithAccessToTheProductGetsTheNotice() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		String member = "member_" + UUID.randomUUID();
		String productId = mapper.readTree(mvc.perform(post("/api/products")
				.header("Authorization", TestJwks.token().organization(company.organization(), "member").subject(member).bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto do membro " + UUID.randomUUID() + "\",\"context\":{}}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
		Client client = ready(company.organization(), member, "member");
		var own = new InternalModuleWorld.Company(company.organization(), company.tenantId(), productId, company.admin());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(own)
				.finding(InternalModuleWorld.finding(InternalModuleWorld.signature("do membro"))).build())
			.andExpect(status().isCreated());
		assertThat(next(client).get("productId").asString()).isEqualTo(productId);
	}

	@Test
	void recurrentNoticeIsTheOnlyChangeWhenItIsTheStrongestAndCarriesSeverity() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		Client admin = ready(company.organization(), "admin_recurrent", "admin");

		String born = InternalModuleWorld.signature("nasce " + UUID.randomUUID());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(born).recurring(true)).build())
			.andExpect(status().isCreated());
		JsonNode created = next(admin);
		assertThat(created.get("change").asString()).isEqualTo("CREATED");
		assertThat(created.get("gravidade").asString()).isEqualTo("ERROR");
		assertThat(created.get("occurrenceType").asString()).isEqualTo("ERROR");
		assertThat(created.has("tenantId")).isFalse();

		String closed = InternalModuleWorld.signature("fecha " + UUID.randomUUID());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(closed)).build())
			.andExpect(status().isCreated());
		assertThat(next(admin).get("change").asString()).isEqualTo("CREATED");
		mongo.getCollection("Ocorrencia").updateOne(Filters.eq("signature", closed),
				new Document("$set", new Document("status", "FECHADA")));
		String closedRevision = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, closed, "ERROR"))).get("estado").get("revisao").asString();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(closed).count(6).revision(closedRevision).recurring(true)).build())
			.andExpect(status().isCreated());
		JsonNode reopened = next(admin);
		assertThat(reopened.get("change").asString()).isEqualTo("REOPENED");
		assertThat(reopened.get("gravidade").asString()).isEqualTo("ERROR");
		assertThat(admin.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

		String open = InternalModuleWorld.signature("aberta " + UUID.randomUUID());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(open).type("WARNING")).build())
			.andExpect(status().isCreated());
		assertThat(next(admin).get("change").asString()).isEqualTo("CREATED");
		String openRevision = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, open, "WARNING"))).get("estado").get("revisao").asString();
		long started = System.nanoTime();
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(open).type("WARNING").count(8).revision(openRevision).recurring(true))
				.build())
			.andExpect(status().isCreated());
		String payload = admin.messages.poll(3, TimeUnit.SECONDS);
		assertThat(payload).isNotNull();
		assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(3));
		JsonNode recurrent = mapper.readTree(payload);
		assertThat(recurrent.get("change").asString()).isEqualTo("RECURRENT");
		assertThat(recurrent.get("gravidade").asString()).isEqualTo("WARNING");
		assertThat(recurrent.get("occurrenceType").asString()).isEqualTo("WARNING");
		assertThat(recurrent.has("tenantId")).isFalse();
		assertThat(admin.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
		Document stored = mongo.getCollection("Ocorrencia").find(Filters.eq("signature", open)).first();
		assertThat(stored.getString("gravidade")).isEqualTo("WARNING");
		assertThat(stored.getString("type")).isEqualTo("WARNING");
	}

}
