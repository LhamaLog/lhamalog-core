package br.com.lhamalog.core.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.product.ProductRepository;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingSessionRevocation;
import br.com.lhamalog.core.support.TestJwks;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Empresa and Produto end to end: real tokens (TestJwks), real tenants and MongoDB (Testcontainers). */
@CoreIntegrationTest
class CompanyProductIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	MongoTemplate mongo;

	@Autowired
	ProductRepository products;

	@Autowired
	RecordingSessionRevocation revocation;

	/** clerkOrgId to tenantId of the tenants created by each test. */
	private final Map<String, String> tenants = new HashMap<>();

	@BeforeEach
	void setUp() {
		revocation.reset();
	}

	/** Creates an ACTIVE tenant for a new organization and returns the organization id. */
	private String organization() {
		String org = "org_test_" + UUID.randomUUID();
		String tenantId = UUID.randomUUID().toString();
		mongo.insert(new TenantDocument(tenantId, org, "Tenant de teste", "ACTIVE"));
		tenants.put(org, tenantId);
		return org;
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String organization,
			String user, String role) {
		return request.header(HttpHeaders.AUTHORIZATION,
				TestJwks.token().organization(organization, role).subject(user).bearer());
	}

	@Test
	void companyPersistsInTenantAndIsIsolatedAndRejectsStaleVersion() throws Exception {
		String org = organization();
		String tenantId = tenants.get(org);
		String body = """
				{"data":{"legalName":"Empresa A","tradeName":"A","document":"","size":"","registrationDate":null},
				 "context":{"sector":"Tecnologia","mission":"","targetAudience":"","differentiators":"","language":""},
				 "address":{"street":"Rua A","number":"1"},"version":null}
				""";
		mvc.perform(as(put("/api/company"), org, "user_a", "member").contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(tenantId))
			.andExpect(jsonPath("$.clerkOrgId").value(org))
			.andExpect(jsonPath("$.version").value(0));
		mvc.perform(as(get("/api/company"), org, "user_a", "member"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.legalName").value("Empresa A"));
		assertEquals("Empresa A", mongo.findById(tenantId, Company.class).data().legalName());
		// The tenant fields owned by the tenant module are kept.
		TenantDocument tenant = mongo.findById(tenantId, TenantDocument.class);
		assertThat(tenant.status()).isEqualTo("ACTIVE");
		assertThat(tenant.name()).isEqualTo("Tenant de teste");
		mvc.perform(as(get("/api/company"), organization(), "user_a", "admin"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.legalName").value(""));
		mvc.perform(as(put("/api/company"), org, "user_a", "member").contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isConflict());
	}

	@Test
	void companyRequiresAuthenticationAndOrganizationAndValidBody() throws Exception {
		mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").header(HttpHeaders.AUTHORIZATION,
				TestJwks.token().without("o").subject("user_no_org").bearer()))
			.andExpect(status().isForbidden());
		assertThat(revocation.revokedUsers()).containsExactly("user_no_org");
		mvc.perform(as(put("/api/company"), organization(), "user", "member").contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.data").exists());
	}

	@Test
	void creatorReceivesAccessButOtherMembersAndTenantsDoNot() throws Exception {
		String org = organization();
		String tenantId = tenants.get(org);
		String response = mvc
			.perform(as(post("/api/products"), org, "creator", "member").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto A\",\"context\":{}}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode created = mapper.readTree(response);
		String id = created.get("id").asText();
		assertEquals(tenantId, products.findById(id).orElseThrow().tenantId());
		mvc.perform(as(get("/api/products"), org, "creator", "member")).andExpect(jsonPath("$[0].id").value(id));
		mvc.perform(as(get("/api/products"), org, "other", "member")).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(as(get("/api/products/" + id), org, "other", "member")).andExpect(status().isNotFound());
		mvc.perform(as(get("/api/products/" + id), org, "admin", "admin")).andExpect(status().isOk());
		mvc.perform(as(get("/api/products/" + id), organization(), "admin", "admin")).andExpect(status().isNotFound());
		String update = "{\"context\":{\"problemSolved\":\"Problema resolvido\"},\"version\":"
				+ created.get("version").asLong() + "}";
		mvc.perform(as(put("/api/products/" + id + "/context"), organization(), "creator", "admin")
			.contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isNotFound());
		mvc.perform(as(put("/api/products/" + id + "/context"), org, "other", "member")
			.contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isNotFound());
		mvc.perform(as(put("/api/products/" + id + "/context"), org, "creator", "member")
			.contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isOk());
		mvc.perform(as(get("/api/products/" + id), org, "creator", "member"))
			.andExpect(jsonPath("$.context.problemSolved").value("Problema resolvido"));
		mvc.perform(as(put("/api/products/" + id + "/context"), org, "creator", "member")
			.contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isConflict());
		mongo.insert(new ProductAccess(id + ":other", tenantId, id, "other"));
		mvc.perform(as(get("/api/products/" + id), org, "other", "member")).andExpect(status().isOk());
		// Business denials are 404, never 403: no session is revoked.
		assertThat(revocation.revokedUsers()).isEmpty();
	}

	@Test
	void validatesProductNameAndAcceptsEmptyOptionalContext() throws Exception {
		String org = organization();
		mvc.perform(as(post("/api/products"), org, "user", "member").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"   \",\"context\":{}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").exists());
		for (int index = 0; index < 5; index++) {
			mvc.perform(as(post("/api/products"), org, "user", "member").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto\",\"context\":{}}")).andExpect(status().isCreated());
		}
		mvc.perform(as(get("/api/products"), org, "user", "member")).andExpect(jsonPath("$.length()").value(5));
	}

}
