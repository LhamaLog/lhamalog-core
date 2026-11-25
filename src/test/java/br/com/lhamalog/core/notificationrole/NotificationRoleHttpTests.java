package br.com.lhamalog.core.notificationrole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 011 (LLT-120), US3: catalog of notification roles (ADR_136 to ADR_140, USID_052). */
@CoreIntegrationTest
class NotificationRoleHttpTests {
	static final String BASE = "/api/company/notification-roles";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@Test
	void firstListCreatesTheFiveDefaultRolesOnlyOnceEvenConcurrently() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> calls = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				calls.add(pool.submit(() -> {
					start.await();
					return mvc.perform(get(BASE).with(admin)).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> call : calls) {
				assertEquals(200, call.get());
			}
		}
		finally {
			pool.shutdownNow();
		}
		mvc.perform(get(BASE).with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(5))
				.andExpect(jsonPath("$.roles[0].name").value("Dono"))
				.andExpect(jsonPath("$.roles[0].defaultKey").value("OWNER"))
				.andExpect(jsonPath("$.roles[0].immutable").value(true))
				.andExpect(jsonPath("$.roles[0].membersSource").value("ACCESS_ROLE"))
				.andExpect(jsonPath("$.roles[1].name").value("Administrador"))
				.andExpect(jsonPath("$.roles[1].immutable").value(true))
				.andExpect(jsonPath("$.roles[2].name").value("Gestor"))
				.andExpect(jsonPath("$.roles[2].immutable").value(false))
				.andExpect(jsonPath("$.roles[2].membersSource").value("EXPLICIT"))
				.andExpect(jsonPath("$.roles[2].severities.length()").value(0))
				.andExpect(jsonPath("$.roles[3].name").value("Supervisor"))
				.andExpect(jsonPath("$.roles[4].name").value("Membro"))
				.andExpect(jsonPath("$.roles[4].isDefault").value(true));
		assertEquals(5, mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(org))),
				"NotificationRole"));
	}

	@Test
	void customRoleIsCreatedWithItsListsWithoutRepetition() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		create(admin, "  Plantão noturno ", "[\"ERROR\",\"WARNING\",\"ERROR\"]", "[\"OCCURRENCE\"]")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Plantão noturno"))
				.andExpect(jsonPath("$.isDefault").value(false))
				.andExpect(jsonPath("$.immutable").value(false))
				.andExpect(jsonPath("$.defaultKey").isEmpty())
				.andExpect(jsonPath("$.severities.length()").value(2))
				.andExpect(jsonPath("$.alertTypes[0]").value("OCCURRENCE"))
				.andExpect(jsonPath("$.memberUserIds.length()").value(0))
				.andExpect(jsonPath("$.membersSource").value("EXPLICIT"));
		mvc.perform(get(BASE).with(admin)).andExpect(jsonPath("$.roles.length()").value(6));
	}

	@Test
	void namesAreUniqueIgnoringCaseAndSpacesButADeletedNameCanBeReused() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode role = json(create(admin, "Plantão", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isCreated()));
		create(admin, "  PLANTÃO ", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("role_name_in_use"))
				.andExpect(jsonPath("$.message").value("Já existe um cargo com este nome nesta empresa."));
		create(admin, "gestor", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("role_name_in_use"));
		JsonNode other = json(create(admin, "Outro", "[\"INFO\"]", "[\"SYSTEM_ALERT\"]").andExpect(status().isCreated()));
		rename(admin, other, "plantão").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("role_name_in_use"));

		mvc.perform(delete(BASE + "/" + id(role)).with(admin)).andExpect(status().isNoContent());
		create(admin, "Plantão", "[\"WARNING\"]", "[\"CRITICAL_STATE\"]").andExpect(status().isCreated());
	}

	@Test
	void ownerAndAdministratorRolesCannotBeChanged() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode roles = json(mvc.perform(get(BASE).with(admin)).andExpect(status().isOk())).get("roles");
		for (JsonNode fixed : List.of(roles.get(0), roles.get(1))) {
			rename(admin, fixed, "Novo nome").andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("immutable_role"))
					.andExpect(jsonPath("$.message").value("Este cargo é padrão e não pode ser alterado."));
			mvc.perform(delete(BASE + "/" + id(fixed)).with(admin)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("immutable_role"));
			mvc.perform(put(BASE + "/" + id(fixed) + "/severities").with(admin).contentType(MediaType.APPLICATION_JSON)
					.content("{\"severities\":[\"ERROR\"],\"version\":" + fixed.get("version").asLong() + "}"))
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("immutable_role"));
			mvc.perform(put(BASE + "/" + id(fixed) + "/alert-types").with(admin).contentType(MediaType.APPLICATION_JSON)
					.content("{\"alertTypes\":[\"OCCURRENCE\"],\"version\":" + fixed.get("version").asLong() + "}"))
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("immutable_role"));
		}
	}

	@Test
	void editableRoleCanBeRenamedConfiguredAndDeletedForGood() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode manager = json(mvc.perform(get(BASE).with(admin))).get("roles").get(2);
		JsonNode renamed = json(rename(admin, manager, "Gerente").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Gerente")).andExpect(jsonPath("$.defaultKey").value("MANAGER")));
		JsonNode configured = json(mvc.perform(put(BASE + "/" + id(manager) + "/severities").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"severities\":[\"ERROR\",\"WARNING\"],\"version\":" + renamed.get("version").asLong() + "}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.severities.length()").value(2)));
		mvc.perform(put(BASE + "/" + id(manager) + "/alert-types").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"alertTypes\":[\"CRITICAL_STATE\"],\"version\":" + configured.get("version").asLong() + "}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.alertTypes[0]").value("CRITICAL_STATE"));

		mvc.perform(delete(BASE + "/" + id(manager)).with(admin)).andExpect(status().isNoContent());
		mvc.perform(get(BASE + "/" + id(manager)).with(admin)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("role_not_found"))
				.andExpect(jsonPath("$.message").value("Cargo não encontrado."));
		mvc.perform(delete(BASE + "/" + id(manager)).with(admin)).andExpect(status().isNotFound());
		mvc.perform(get(BASE).with(admin)).andExpect(jsonPath("$.roles.length()").value(4))
				.andExpect(jsonPath("$.roles[?(@.defaultKey=='MANAGER')]").isEmpty());
	}

	@Test
	void roleOfAnotherCompanyIsNotFound() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		var other = session(organization(), "user_other", "admin");
		JsonNode role = json(create(admin, "Plantão", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isCreated()));
		mvc.perform(get(BASE + "/" + id(role)).with(other)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("role_not_found"));
		mvc.perform(delete(BASE + "/" + id(role)).with(other)).andExpect(status().isNotFound());
		rename(other, role, "Meu").andExpect(status().isNotFound());
	}

	@Test
	void invalidNamesAndListsPointToTheField() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		create(admin, "Sem gravidade", "[]", "[\"OCCURRENCE\"]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.severities").exists());
		create(admin, "Tipo errado", "[\"ERROR\"]", "[\"FATAL\"]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.alertTypes").exists());
		create(admin, "   ", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
		create(admin, "a".repeat(61), "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
		mvc.perform(post(BASE).with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sem listas\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.severities").exists())
				.andExpect(jsonPath("$.fieldErrors.alertTypes").exists());

		JsonNode supervisor = json(mvc.perform(get(BASE).with(admin))).get("roles").get(3);
		mvc.perform(put(BASE + "/" + id(supervisor) + "/severities").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"severities\":[],\"version\":" + supervisor.get("version").asLong() + "}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.severities").exists());
		mvc.perform(patch(BASE + "/" + id(supervisor)).with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Sem versão\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.version").exists());
	}

	@Test
	void staleVersionIsAConflict() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode role = json(create(admin, "Plantão", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isCreated()));
		rename(admin, role, "Plantão 2").andExpect(status().isOk());
		rename(admin, role, "Plantão 3").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Os dados foram alterados. Recarregue antes de salvar novamente."));
	}

	@Test
	void membersCannotUseTheRoleRoutes() throws Exception {
		String org = organization();
		var member = session(org, "user_member", "member");
		mvc.perform(get(BASE).with(member)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"));
		create(member, "Plantão", "[\"ERROR\"]", "[\"OCCURRENCE\"]").andExpect(status().isForbidden());
		assertEquals(0, mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(org))),
				"NotificationRole"), "a refused member must not seed the defaults");
	}

	@Test
	void indexesOfTheDataModelExistWithTheirPartialFilters() {
		var indexes = mongo.indexOps("NotificationRole").getIndexInfo();
		var byName = new java.util.HashMap<String, org.springframework.data.mongodb.core.index.IndexInfo>();
		indexes.forEach((index) -> byName.put(index.getName(), index));
		assertEquals(true, byName.get("notification_role_tenant_name").isUnique());
		assertEquals(true, byName.get("notification_role_tenant_name").getPartialFilterExpression().contains("deleted"));
		assertEquals(true, byName.get("notification_role_tenant_default").isUnique());
		assertEquals(true, byName.get("notification_role_tenant_default").getPartialFilterExpression().contains("$type"));
		assertEquals(false, byName.get("notification_role_tenant_member").isUnique());
	}

	private ResultActions create(RequestPostProcessor actor, String name, String severities, String alertTypes)
			throws Exception {
		return mvc.perform(post(BASE).with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"severities\":" + severities + ",\"alertTypes\":" + alertTypes + "}"));
	}

	private ResultActions rename(RequestPostProcessor actor, JsonNode role, String name) throws Exception {
		return mvc.perform(patch(BASE + "/" + id(role)).with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"version\":" + role.get("version").asLong() + "}"));
	}

	private static String id(JsonNode role) {
		return role.get("id").asText();
	}

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private RequestPostProcessor session(String org, String user, String role) {
		return tenants().session(org, user, role);
	}

	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private String organization() {
		return "org_nr_" + UUID.randomUUID();
	}
}
