package br.com.lhamalog.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.logging.CorrelationId;
import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingCollectorManualUploadClient;
import br.com.lhamalog.core.support.TestTenants;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** HTTP contract of {@code POST /api/manual/upload} (LLT-292, T011–T015). */
@CoreIntegrationTest
class ManualUploadHttpTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	MongoTemplate mongo;

	@Autowired
	RecordingCollectorManualUploadClient collector;

	private TestTenants tenants;

	@BeforeEach
	void resetCollector() {
		collector.reset();
	}

	@AfterEach
	void resetCollectorAfter() {
		collector.reset();
	}

	@Test
	void rejectsMissingOrInvalidCredentials() throws Exception {
		mvc.perform(upload(file("app.log", "hello")).param("productId", "p1")).andExpect(status().isUnauthorized());
		mvc.perform(upload(file("app.log", "hello")).param("productId", "p1").header(HttpHeaders.AUTHORIZATION,
				"Bearer invalid")).andExpect(status().isUnauthorized());
		assertTrue(collector.requests().isEmpty());
	}

	@Test
	void acceptedUploadForwardsProductFilesAndCorrelationId() throws Exception {
		String organization = organization();
		RequestPostProcessor actor = session(organization, "owner", "member");
		String productId = createProduct(actor, "App");
		String tenantId = tenants().tenantId(organization);
		mvc.perform(upload(file("app.log", "hello-world")).param("productId", productId)
			.header(CorrelationId.HEADER, "cid-manual-1")
			.with(actor))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.loteId").isNotEmpty())
			.andExpect(jsonPath("$.files.length()").value(1))
			.andExpect(jsonPath("$.files[0].name").value("app.log"))
			.andExpect(jsonPath("$.files[0].status").value(ManualUploadResponses.ACCEPTED))
			.andExpect(jsonPath("$.files[0].fileId").isNotEmpty())
			.andExpect(jsonPath("$.files[0].batchId").isNotEmpty());
		assertEquals(1, collector.requests().size());
		CollectorManualUploadClient.Request request = collector.requests().get(0);
		assertEquals(productId, request.productId());
		assertEquals(tenantId, request.tenantId());
		assertEquals("cid-manual-1", request.correlationId());
		assertEquals("app.log", request.files().get(0).filename());
	}

	@Test
	void doesNotCallTheCollectorWhenValidationFails() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		mvc.perform(upload(file("notes.exe", "MZ")).param("productId", productId).with(actor))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.files[0].status").value(ManualUploadResponses.INVALID_EXTENSION));
		assertTrue(collector.requests().isEmpty());
	}

	@Test
	void returns403WhenTheProductIsUnknownOfAnotherTenantWithoutAccessOrArchived() throws Exception {
		String organization = organization();
		RequestPostProcessor owner = session(organization, "owner", "member");
		RequestPostProcessor admin = session(organization, "admin", "admin");
		String productId = createProduct(owner, "App");
		JsonNode created = mapper.readTree(mvc
			.perform(post("/api/products").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"To archive\",\"context\":{\"functionalDescription\":\"ctx\"}}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
		String archivedId = created.get("id").asText();
		mvc.perform(post("/api/products/" + archivedId + "/archive").with(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":" + created.get("version").asLong() + "}"))
			.andExpect(status().isOk());

		assertForbidden(session(organization(), "other-admin", "admin"), productId);
		assertForbidden(session(organization, "outsider", "member"), productId);
		assertForbidden(owner, UUID.randomUUID().toString());
		assertForbidden(admin, archivedId);
		assertTrue(collector.requests().isEmpty());
	}

	@Test
	void allowsAdminWithoutProductAccessAndMemberWithAccess() throws Exception {
		String organization = organization();
		RequestPostProcessor owner = session(organization, "owner", "member");
		String productId = createProduct(owner, "App");
		mvc.perform(upload(file("a.log", "one")).param("productId", productId)
			.with(session(organization, "admin", "admin")))
			.andExpect(status().isAccepted());
		RequestPostProcessor other = session(organization, "other", "member");
		mvc.perform(upload(file("a.log", "one")).param("productId", productId).with(other))
			.andExpect(status().isForbidden());
		mongo.insert(new ProductAccess(productId + ":other", tenants().tenantId(organization), productId, "other"));
		mvc.perform(upload(file("a.log", "one")).param("productId", productId).with(other))
			.andExpect(status().isAccepted());
	}

	@Test
	void mapsCollectorConflictPartialThrottleUnavailableAndTimeout() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");

		collector.respond((request) -> new CollectorManualUploadClient.Response(409,
				new Body(request.loteId(),
						List.of(FileResult.refused("app.log", ManualUploadResponses.DUPLICATE,
								ManualUploadResponses.ALREADY_EXISTS)))));
		mvc.perform(upload(file("app.log", "same")).param("productId", productId).with(actor))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.files[0].status").value(ManualUploadResponses.DUPLICATE));

		collector.reset();
		collector.respond((request) -> new CollectorManualUploadClient.Response(202,
				new Body(request.loteId(), List.of(
						FileResult.accepted("ok.log", "file-ok", "batch-ok"),
						FileResult.refused("dup.log", ManualUploadResponses.DUPLICATE,
								ManualUploadResponses.ALREADY_EXISTS)))));
		mvc.perform(upload(file("ok.log", "ok"), file("dup.log", "dup")).param("productId", productId).with(actor))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.files.length()").value(2))
			.andExpect(jsonPath("$.loteId").isNotEmpty());

		collector.reset();
		collector.respond(429, new Body("lote-q", List.of()));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isTooManyRequests());

		collector.reset();
		collector.respond(503, new Body("lote-s", List.of()));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isServiceUnavailable());

		collector.reset();
		collector.fail(new CollectorForwardException("Collector request timed out", true, null));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isGatewayTimeout());
	}

	@Test
	void twoFilesShareTheSameLoteId() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		ResultActions result = mvc.perform(upload(file("a.log", "one"), file("b.json", "[1]")).param("productId", productId)
			.with(actor))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.files.length()").value(2));
		JsonNode body = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		assertEquals(body.get("loteId").asText(), collector.requests().get(0).loteId());
		assertEquals(2, collector.requests().get(0).files().size());
	}

	@Test
	void mixesLocalRefusalWithCollectorAcceptedFilesAndKeepsRequestOrder() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		collector.respond((request) -> new CollectorManualUploadClient.Response(202,
				new Body(request.loteId(),
						List.of(FileResult.accepted("ok.log", "file-ok", "batch-ok")))));
		mvc.perform(upload(file("bad.exe", "MZ"), file("ok.log", "ok")).param("productId", productId).with(actor))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.files.length()").value(2))
			.andExpect(jsonPath("$.files[0].name").value("bad.exe"))
			.andExpect(jsonPath("$.files[0].status").value(ManualUploadResponses.INVALID_EXTENSION))
			.andExpect(jsonPath("$.files[1].name").value("ok.log"))
			.andExpect(jsonPath("$.files[1].status").value(ManualUploadResponses.ACCEPTED));
		assertEquals(1, collector.requests().size());
		assertEquals("ok.log", collector.requests().get(0).files().get(0).filename());
	}

	@Test
	void localRefusalTurnsCollectorConflictIntoValidationStatus() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		collector.respond((request) -> new CollectorManualUploadClient.Response(409,
				new Body(request.loteId(),
						List.of(FileResult.refused("ok.log", ManualUploadResponses.DUPLICATE,
								ManualUploadResponses.ALREADY_EXISTS)))));
		mvc.perform(upload(file("ok.log", "ok"), file("bad.exe", "MZ")).param("productId", productId).with(actor))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.files[0].status").value(ManualUploadResponses.DUPLICATE))
			.andExpect(jsonPath("$.files[1].status").value(ManualUploadResponses.INVALID_EXTENSION));
	}

	@Test
	void unexpectedCollectorStatusBecomes503AndUnauthorizedIsLoggedSeparately() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		collector.respond(500, new Body("lote-x", List.of()));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.message").exists())
			.andExpect(jsonPath("$.files").doesNotExist());
		collector.reset();
		collector.respond(401, new Body("lote-u", List.of()));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.files").doesNotExist());
	}

	@Test
	void forwardsRetryAfterFromTheCollector() throws Exception {
		RequestPostProcessor actor = session(organization(), "owner", "member");
		String productId = createProduct(actor, "App");
		org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
		headers.add(org.springframework.http.HttpHeaders.RETRY_AFTER, "30");
		collector.respond((request) -> new CollectorManualUploadClient.Response(429, new Body(request.loteId(), List.of()),
				headers));
		mvc.perform(upload(file("app.log", "x")).param("productId", productId).with(actor))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string(org.springframework.http.HttpHeaders.RETRY_AFTER, "30"));
	}

	private void assertForbidden(RequestPostProcessor actor, String productId) throws Exception {

		mvc.perform(upload(file("app.log", "hello")).param("productId", productId).with(actor))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ProductUploadAuthorizer.PRODUCT_FORBIDDEN));
	}

	private String createProduct(RequestPostProcessor actor, String name) throws Exception {
		JsonNode created = mapper.readTree(mvc
			.perform(post("/api/products").with(actor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"context\":{\"functionalDescription\":\"Contexto\"}}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
		return created.get("id").asText();
	}

	private RequestPostProcessor session(String organization, String user, String role) {
		return tenants().session(organization, user, role);
	}

	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private static String organization() {
		return "org_upload_" + UUID.randomUUID();
	}

	private static MockMultipartHttpServletRequestBuilder upload(MockMultipartFile... files) {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/manual/upload");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		return request;
	}

	private static MockMultipartFile file(String filename, String content) {
		return new MockMultipartFile("files", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
	}
}
