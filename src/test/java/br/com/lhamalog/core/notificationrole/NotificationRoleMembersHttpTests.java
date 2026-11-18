package br.com.lhamalog.core.notificationrole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 011 (LLT-120), US4: people in notification roles (ADR_139/140, USID_052, LLT-170). */
@CoreIntegrationTest
class NotificationRoleMembersHttpTests {
	static final String BASE = "/api/company/notification-roles";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@Test
	void addingAndRemovingAPersonAreIdempotent() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode manager = defaultRole(admin, 2);
		mvc.perform(post(BASE + "/" + id(manager) + "/members/user_bruno").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.memberUserIds[0]").value("user_bruno"));
		mvc.perform(post(BASE + "/" + id(manager) + "/members/user_bruno").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.memberUserIds.length()").value(1));

		mvc.perform(delete(BASE + "/" + id(manager) + "/members/user_bruno").with(admin)).andExpect(status().isNoContent());
		mvc.perform(delete(BASE + "/" + id(manager) + "/members/user_bruno").with(admin)).andExpect(status().isNoContent());
		mvc.perform(get(BASE + "/" + id(manager)).with(admin)).andExpect(jsonPath("$.memberUserIds.length()").value(0));
	}

	@Test
	void replacingTheWholeListNeedsTheCurrentVersion() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode manager = defaultRole(admin, 2);
		JsonNode replaced = json(replaceMembers(admin, manager, "[\"user_a\",\"user_b\",\"user_a\"]", manager.get("version").asLong())
				.andExpect(status().isOk()).andExpect(jsonPath("$.memberUserIds.length()").value(2)));
		replaceMembers(admin, manager, "[\"user_c\"]", manager.get("version").asLong()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Os dados foram alterados. Recarregue antes de salvar novamente."));
		replaceMembers(admin, manager, "[\"user_c\"]", replaced.get("version").asLong()).andExpect(status().isOk())
				.andExpect(jsonPath("$.memberUserIds[0]").value("user_c"))
				.andExpect(jsonPath("$.memberUserIds.length()").value(1));
	}

	@Test
	void ownerAndAdministratorRolesTakeTheirMembersFromTheAccessRole() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		for (int index : new int[] { 0, 1 }) {
			JsonNode fixed = defaultRole(admin, index);
			mvc.perform(post(BASE + "/" + id(fixed) + "/members/user_bruno").with(admin)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("members_from_access_role"))
					.andExpect(jsonPath("$.message").value("Os membros deste cargo vêm do papel de acesso."));
			mvc.perform(delete(BASE + "/" + id(fixed) + "/members/user_bruno").with(admin))
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("members_from_access_role"));
			replaceMembers(admin, fixed, "[\"user_bruno\"]", fixed.get("version").asLong())
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("members_from_access_role"));
		}
	}

	@Test
	void rolesOfAPersonListExplicitRolesAndTheAccessRoleButNotDeletedOnes() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		JsonNode manager = defaultRole(admin, 2);
		JsonNode supervisor = defaultRole(admin, 3);
		mvc.perform(post(BASE + "/" + id(manager) + "/members/user_bruno").with(admin)).andExpect(status().isOk());
		mvc.perform(post(BASE + "/" + id(supervisor) + "/members/user_bruno").with(admin)).andExpect(status().isOk());

		mvc.perform(get("/api/company/members/user_bruno/notification-roles").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user_bruno"))
				.andExpect(jsonPath("$.roles.length()").value(2))
				.andExpect(jsonPath("$.roles[0].name").value("Gestor"))
				.andExpect(jsonPath("$.roles[1].name").value("Supervisor"));

		mvc.perform(delete(BASE + "/" + id(supervisor)).with(admin)).andExpect(status().isNoContent());
		mvc.perform(get("/api/company/members/user_bruno/notification-roles").with(admin))
				.andExpect(jsonPath("$.roles.length()").value(1))
				.andExpect(jsonPath("$.roles[0].name").value("Gestor"));

		mongo.insert(new MemberDocument(UUID.randomUUID().toString(), tenants().tenantId(org), "user_owner", null, "DONO",
				null, null, 1, 0, null, null));
		mvc.perform(get("/api/company/members/user_owner/notification-roles").with(admin))
				.andExpect(jsonPath("$.roles.length()").value(1))
				.andExpect(jsonPath("$.roles[0].name").value("Dono"));
		mvc.perform(get("/api/company/members/user_admin/notification-roles").with(admin))
				.andExpect(jsonPath("$.roles[0].name").value("Administrador"));
	}

	@Test
	void aFullRoleRefusesANewPersonButKeepsTheOnesInside() throws Exception {
		var admin = session(organization(), "user_admin", "admin");
		JsonNode member = defaultRole(admin, 4);
		String full = IntStream.range(0, 200).mapToObj((i) -> "\"user_" + i + "\"").collect(Collectors.joining(","));
		replaceMembers(admin, member, "[" + full + "]", member.get("version").asLong()).andExpect(status().isOk())
				.andExpect(jsonPath("$.memberUserIds.length()").value(200));
		mvc.perform(post(BASE + "/" + id(member) + "/members/user_201").with(admin)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("role_members_limit"))
				.andExpect(jsonPath("$.message").value("O cargo já tem 200 membros."));
		mvc.perform(post(BASE + "/" + id(member) + "/members/user_7").with(admin)).andExpect(status().isOk());

		String tooMany = IntStream.range(0, 201).mapToObj((i) -> "\"u" + i + "\"").collect(Collectors.joining(","));
		JsonNode current = json(mvc.perform(get(BASE + "/" + id(member)).with(admin)));
		replaceMembers(admin, member, "[" + tooMany + "]", current.get("version").asLong())
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.memberUserIds").exists());
	}

	@Test
	void invalidUserIdAndMemberSessionsAreRefused() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		JsonNode manager = defaultRole(admin, 2);
		String longUser = "u".repeat(201);
		mvc.perform(post(BASE + "/" + id(manager) + "/members/" + longUser).with(admin)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Informe o usuário."));
		mvc.perform(get("/api/company/members/" + longUser + "/notification-roles").with(admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Informe o usuário."));
		mvc.perform(post(BASE + "/missing-role/members/user_bruno").with(admin)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("role_not_found"));

		var member = session(org, "user_member", "member");
		mvc.perform(post(BASE + "/" + id(manager) + "/members/user_member").with(member)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(get("/api/company/members/user_member/notification-roles").with(member))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
	}

	private JsonNode defaultRole(RequestPostProcessor admin, int index) throws Exception {
		return json(mvc.perform(get(BASE).with(admin)).andExpect(status().isOk())).get("roles").get(index);
	}

	private ResultActions replaceMembers(RequestPostProcessor actor, JsonNode role, String members, long version)
			throws Exception {
		return mvc.perform(put(BASE + "/" + id(role) + "/members").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memberUserIds\":" + members + ",\"version\":" + version + "}"));
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
		return "org_nrm_" + UUID.randomUUID();
	}
}
