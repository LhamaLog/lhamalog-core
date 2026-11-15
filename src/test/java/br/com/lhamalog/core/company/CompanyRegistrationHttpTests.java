package br.com.lhamalog.core.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.TestcontainersConfiguration;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.product.ProductRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"management.otlp.metrics.export.enabled=false",
		"management.tracing.export.otlp.enabled=false"
})
@AutoConfigureMockMvc
class CompanyRegistrationHttpTests {
	private static final String ADMIN_ONLY = "Somente o administrador da empresa pode alterar este cadastro.";
	private static final String SELECT_COMPANY = "Selecione uma empresa para continuar.";
	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0};

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ProductRepository products;
	@Autowired org.springframework.data.mongodb.core.MongoTemplate mongo;

	@Test
	void adminRegistersAndUpdatesOwnCompany() throws Exception {
		String tenant = organization();
		RequestPostProcessor admin = session(tenant, "admin_a", "admin");
		MvcResult created = mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa A", "Fantasia", null, "Atuacao inicial")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tenant))
				.andExpect(jsonPath("$.data.legalName").value("Empresa A"))
				.andExpect(jsonPath("$.data.tradeName").value("Fantasia")).andReturn();
		long version = version(created);
		mvc.perform(get("/api/company").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.legalName").value("Empresa A"));
		MvcResult updated = mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa B", null, version, "Atuacao inicial")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").value("Empresa B"))
				.andExpect(jsonPath("$.data.tradeName").value("Fantasia")).andReturn();
		long current = version(updated);
		mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa C", "Outra", version, "Atuacao inicial")))
				.andExpect(status().isConflict());
		mvc.perform(get("/api/company").with(admin)).andExpect(jsonPath("$.data.legalName").value("Empresa B"))
				.andExpect(jsonPath("$.data.tradeName").value("Fantasia"));
		MockMultipartFile logo = new MockMultipartFile("file", "logo.png", "image/png", PNG);
		MvcResult withLogo = mvc.perform(multipart("/api/company/logo").file(logo).param("version", Long.toString(current)).with(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.logo.present").value(true)).andReturn();
		mvc.perform(get("/api/company/logo").with(admin)).andExpect(status().isOk());
		mvc.perform(delete("/api/company/logo").param("version", Long.toString(version(withLogo))).with(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.logo.present").value(false));
		mvc.perform(get("/api/company/logo").with(admin)).andExpect(status().isNotFound());
	}

	@Test
	void memberReadsAndCannotChange() throws Exception {
		String tenant = organization();
		RequestPostProcessor admin = session(tenant, "admin_a", "admin");
		RequestPostProcessor member = session(tenant, "member_a", "member");
		mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa M", "Marca", null, "Contexto do membro"))).andExpect(status().isOk());
		mvc.perform(multipart("/api/company/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
				.param("version", "0").with(admin)).andExpect(status().isOk());
		mvc.perform(get("/api/company").with(member)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.legalName").value("Empresa M"));
		mvc.perform(get("/api/company/logo").with(member)).andExpect(status().isOk());
		mvc.perform(get("/api/company/context/revisions").with(member)).andExpect(status().isOk());
		assertForbidden(put("/api/company").with(member).contentType(MediaType.APPLICATION_JSON).content("{"));
		assertForbidden(put("/api/company").with(member).contentType(MediaType.APPLICATION_JSON).content("{}"));
		assertForbidden(multipart("/api/company/logo").with(member));
		assertForbidden(delete("/api/company/logo").with(member));
		mvc.perform(get("/api/company").with(member)).andExpect(jsonPath("$.data.legalName").value("Empresa M"));
	}

	@Test
	void nonAdminSlugCannotChange() throws Exception {
		String tenant = organization();
		mvc.perform(put("/api/company").with(session(tenant, "admin_a", "admin")).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa P", null, null, "Contexto"))).andExpect(status().isOk());
		for (String role : new String[] {"owner", "Administrador"}) {
			RequestPostProcessor actor = session(tenant, "user_" + role, role);
			mvc.perform(get("/api/company").with(actor)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.legalName").value("Empresa P"));
			assertForbidden(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content("{}"));
			assertForbidden(delete("/api/company").with(actor));
		}
	}

	@Test
	void sessionRulesStayDistinct() throws Exception {
		mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/company").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").with(jwt())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SELECT_COMPANY));
		mvc.perform(put("/api/company").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(SELECT_COMPANY));
		mvc.perform(delete("/api/company").with(jwt())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SELECT_COMPANY));
	}

	@Test
	void adminDeletesRegistrationAndMemberCannot() throws Exception {
		String tenant = organization();
		RequestPostProcessor admin = session(tenant, "admin_a", "admin");
		RequestPostProcessor member = session(tenant, "member_a", "member");
		MvcResult created = mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa D", "Marca", null, "Contexto para excluir"))).andExpect(status().isOk()).andReturn();
		long version = version(created);
		mvc.perform(multipart("/api/company/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
				.param("version", Long.toString(version)).with(admin)).andExpect(status().isOk());
		String productJson = mvc.perform(post("/api/products").with(member).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto A\",\"context\":{}}")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String productId = mapper.readTree(productJson).get("id").asText();
		assertForbidden(delete("/api/company").with(member));
		mvc.perform(get("/api/company").with(admin)).andExpect(jsonPath("$.data.legalName").value("Empresa D"));
		String removed = mvc.perform(delete("/api/company").with(admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertEmpty(removed, tenant);
		mvc.perform(get("/api/company/logo").with(admin)).andExpect(status().isNotFound());
		mvc.perform(get("/api/company/context/revisions").with(admin)).andExpect(jsonPath("$.revisions.length()").value(0));
		assertEquals(tenant, products.findById(productId).orElseThrow().tenantId());
		assertTrue(mongo.findById(productId + ":" + "member_a", ProductAccess.class) != null
				|| !mongo.findAll(ProductAccess.class).stream().filter(access -> tenant.equals(access.tenantId())).toList().isEmpty());
		String second = mvc.perform(delete("/api/company").with(admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertEmpty(second, tenant);
		mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa Nova", null, null, "Nova atuacao")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").value("Empresa Nova"));
		mvc.perform(get("/api/company").with(admin)).andExpect(jsonPath("$.data.tradeName").value(""))
				.andExpect(jsonPath("$.context.description").value("Nova atuacao"));
		mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa Velha", null, version, "Antiga"))).andExpect(status().isConflict());
		mvc.perform(get("/api/company").with(admin)).andExpect(jsonPath("$.data.legalName").value("Empresa Nova"));
	}

	@Test
	void operationsStayInsideTheSessionOrganization() throws Exception {
		String tenantA = organization();
		String tenantB = organization();
		RequestPostProcessor adminA = session(tenantA, "admin_a", "admin");
		RequestPostProcessor adminB = session(tenantB, "admin_b", "admin");
		RequestPostProcessor memberA = session(tenantA, "member_a", "member");
		mvc.perform(put("/api/company").with(adminA).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa A", null, null, "Contexto A"))).andExpect(status().isOk());
		mvc.perform(put("/api/company").with(adminB).contentType(MediaType.APPLICATION_JSON)
				.content(registration("Empresa B", null, null, "Contexto B"))).andExpect(status().isOk());
		mvc.perform(get("/api/company").with(adminA)).andExpect(jsonPath("$.data.legalName").value("Empresa A"))
				.andExpect(jsonPath("$.id").value(tenantA));
		mvc.perform(put("/api/company").with(adminA).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"organizationId":"%s","clerkOrgId":"%s","data":{"legalName":"So A"},"context":{},"address":{},"version":0}
						""".formatted(tenantB, tenantB))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(tenantA)).andExpect(jsonPath("$.data.legalName").value("So A"));
		mvc.perform(get("/api/company").with(adminB)).andExpect(jsonPath("$.data.legalName").value("Empresa B"));
		assertForbidden(put("/api/company").with(memberA).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"organizationId":"%s","data":{"legalName":"Invasao"},"context":{},"address":{},"version":0}
						""".formatted(tenantB)));
		mvc.perform(get("/api/company").with(adminA)).andExpect(jsonPath("$.data.legalName").value("So A"));
		mvc.perform(get("/api/company").with(adminB)).andExpect(jsonPath("$.data.legalName").value("Empresa B"));
		mvc.perform(delete("/api/company").with(adminA)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tenantA));
		mvc.perform(get("/api/company").with(adminB)).andExpect(jsonPath("$.data.legalName").value("Empresa B"))
				.andExpect(jsonPath("$.context.description").value("Contexto B"));
	}

	@Test
	void unavailableActionsStayForAdminAndMember() throws Exception {
		String tenant = organization();
		for (RequestPostProcessor actor : new RequestPostProcessor[] {session(tenant, "admin_a", "admin"), session(tenant, "member_a", "member")}) {
			mvc.perform(get("/api/company/postal-code/87000000").with(actor)).andExpect(status().isNotImplemented());
			mvc.perform(post("/api/company/actions/export-pdf").with(actor)).andExpect(status().isNotImplemented());
			mvc.perform(post("/api/company/actions/sync").with(actor)).andExpect(status().isNotImplemented());
		}
	}

	private void assertForbidden(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(ADMIN_ONLY));
	}

	private void assertEmpty(String json, String tenant) throws Exception {
		JsonNode body = mapper.readTree(json);
		assertEquals(tenant, body.get("id").asText());
		assertTrue(body.get("version") == null || body.get("version").isNull());
		assertEquals("", body.get("data").get("legalName").asText());
		assertTrue(body.get("logo") == null || !body.get("logo").path("present").asBoolean());
	}

	private long version(MvcResult result) throws Exception {
		return mapper.readTree(result.getResponse().getContentAsString()).get("version").asLong();
	}

	private static String registration(String legalName, String tradeName, Long version, String description) {
		String trade = tradeName == null ? "" : "\"tradeName\":\"" + tradeName + "\",";
		String versionJson = version == null ? "null" : Long.toString(version);
		return """
				{"data":{"legalName":"%s",%s"document":""},"context":{"description":"%s"},"address":{},"version":%s}
				""".formatted(legalName, trade, description, versionJson);
	}

	private RequestPostProcessor session(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", organization, "rol", role)));
	}

	private String organization() {
		return "org_reg_" + UUID.randomUUID();
	}
}
