package br.com.lhamalog.core.company;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"spring.mongodb.uri=${TEST_MONGODB_URI}",
		"management.otlp.metrics.export.enabled=false", "management.tracing.export.otlp.enabled=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class CompanyProductIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	@Autowired ProductRepository products;

	private RequestPostProcessor session(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", organization, "rol", role)));
	}

	private String organization() { return "org_test_" + UUID.randomUUID(); }

	@Test
	void companyPersistsAndIsIsolatedAndRejectsStaleVersion() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "user_a", "admin");
		String body = """
				{"data":{"legalName":"Empresa A","tradeName":"A","document":"","size":"","registrationDate":null},
				 "context":{"sector":"Tecnologia","mission":"","targetAudience":"","differentiators":"","language":""},
				 "address":{"street":"Rua A","number":"1"},"version":null}
				""";
		mvc.perform(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tenant));
		mvc.perform(get("/api/company").with(actor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.legalName").value("Empresa A"));
		assertEquals("Empresa A", mongo.findById(tenant, Company.class).data().legalName());
		mvc.perform(get("/api/company").with(session(organization(), "user_a", "admin")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").value(""));
		mvc.perform(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}

	@Test
	void companySettingsReloadFromTheSameTenant() throws Exception {
		String tenant = organization();
		var actor = session(tenant, "user_a", "admin");
		String body = """
				{"data":{"legalName":"Empresa Persistida","document":"11.444.777/0001-61","email":"pessoa@example.com",
				 "sector":"Tecnologia / SaaS","timezone":"America/Sao_Paulo","language":"pt-BR"},
				 "context":{"segment":"Tecnologia / SaaS","environments":["Produção"],"monitoredSystems":["Backend"]},
				 "address":{"postalCode":"87000000","street":"Avenida Brasil","number":"10","district":"Centro",
				 "city":"Maringá","state":"PR","useAsFiscalAndCorrespondence":true},
				 "preferences":{"dailySummary":true,"aiDetail":"Alto","currency":"BRL","dataInterval":"LAST_24_HOURS"},
				 "version":null}
				""";
		String saved = mvc.perform(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long version = mapper.readTree(saved).get("version").asLong();
		mvc.perform(get("/api/company").with(actor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.preferences.dailySummary").value(true))
				.andExpect(jsonPath("$.preferences.aiDetail").value("Alto"))
				.andExpect(jsonPath("$.shared.sector").value("Tecnologia / SaaS"))
				.andExpect(jsonPath("$.summary.verified").value(false))
				.andExpect(jsonPath("$.data.organizationId").value(tenant));
		String again = """
				{"data":{"email":"pessoa@example.com"},"context":{},"address":{},"preferences":{"aiDetail":"Alto"},
				 "version":%d}
				""".formatted(version);
		mvc.perform(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content(again))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").value("Empresa Persistida"))
				.andExpect(jsonPath("$.context.mission").value(""));
		assertEquals("Alto", mongo.findById(tenant, Company.class).preferences().aiDetail());
	}

	@Test
	void companyRequiresAuthenticationAndOrganizationAndValidBody() throws Exception {
		mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").with(jwt())).andExpect(status().isForbidden());
		mvc.perform(put("/api/company").with(session(organization(), "user", "member"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Somente o administrador da empresa pode alterar este cadastro."));
		mvc.perform(put("/api/company").with(session(organization(), "user", "admin"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.data").exists());
	}

	@Test
	void creatorReceivesAccessButOtherMembersAndTenantsDoNot() throws Exception {
		String tenant = organization();
		var creator = session(tenant, "creator", "member");
		String response = mvc.perform(post("/api/products").with(creator).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto A\",\"context\":{}}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		JsonNode created = mapper.readTree(response);
		String id = created.get("id").asText();
		assertEquals(tenant, products.findById(id).orElseThrow().tenantId());
		mvc.perform(get("/api/products").with(creator)).andExpect(jsonPath("$[0].id").value(id));
		mvc.perform(get("/api/products").with(session(tenant, "other", "member")))
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/products/" + id).with(session(tenant, "other", "member")))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + id).with(session(tenant, "admin", "admin")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/products/" + id).with(session(organization(), "admin", "admin")))
				.andExpect(status().isNotFound());
		String update = "{\"context\":{\"problemSolved\":\"Problema resolvido\"},\"version\":" + created.get("version").asLong() + "}";
		mvc.perform(put("/api/products/" + id + "/context").with(session(organization(), "creator", "admin"))
				.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
		mvc.perform(put("/api/products/" + id + "/context").with(session(tenant, "other", "member"))
				.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
		mvc.perform(put("/api/products/" + id + "/context").with(creator)
				.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
		mvc.perform(get("/api/products/" + id).with(creator))
				.andExpect(jsonPath("$.context.problemSolved").value("Problema resolvido"));
		mvc.perform(put("/api/products/" + id + "/context").with(creator)
				.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
		mongo.insert(new ProductAccess(id + ":other", tenant, id, "other"));
		mvc.perform(get("/api/products/" + id).with(session(tenant, "other", "member")))
				.andExpect(status().isOk());
	}

	@Test
	void validatesProductNameAndAcceptsEmptyOptionalContext() throws Exception {
		var actor = session(organization(), "user", "member");
		mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"   \",\"context\":{}}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
		for (int index = 0; index < 5; index++) {
			mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Produto\",\"context\":{}}"))
					.andExpect(status().isCreated());
		}
		mvc.perform(get("/api/products").with(actor)).andExpect(jsonPath("$.length()").value(5));
	}
}
