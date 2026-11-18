package br.com.lhamalog.core.company;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;

/** Real tokens (TestJwks) and real tenants: the core keeps no invite flow (LLT-167). */
@CoreIntegrationTest
class OrganizationInviteBoundaryTests {
	private static final String ADMIN_ONLY = "Somente o administrador da empresa pode alterar este cadastro.";
	private static final String REGISTRATION = """
			{"data":{"legalName":"Empresa Convidada","document":""},"context":{"description":"Aceite"},"address":{},"organizationId":"org_outra","version":null}
			""";
	private static final String[] INVITE_PATHS = {
			"/api/invitations", "/api/company/invitations", "/api/organization-invitations", "/api/invitations/accept"
	};

	@Autowired MockMvc mvc;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@Test
	void invitePathsStayUnmapped() throws Exception {
		RequestPostProcessor admin = current(organization(), "admin_a", "admin");
		for (String path : INVITE_PATHS) {
			mvc.perform(post(path).with(admin)).andExpect(status().isNotFound());
		}
	}

	@Test
	void invitePathsRejectAnonymous() throws Exception {
		for (String path : INVITE_PATHS) {
			mvc.perform(post(path)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void acceptedMemberIsNotAdministrator() throws Exception {
		String organization = organization();
		mvc.perform(put("/api/company").with(current(organization, "admin_a", "admin"))
				.contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clerkOrgId").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
		mvc.perform(get("/api/company").with(current(organization, "admin_a", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clerkOrgId").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
		for (RequestPostProcessor member : new RequestPostProcessor[] {
				current(organization, "member_current", "member"),
				current(organization, "member_prefixed", "org:member")}) {
			mvc.perform(get("/api/company").with(member)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
			mvc.perform(get("/api/user/profile").with(member)).andExpect(status().isOk());
			mvc.perform(put("/api/company").with(member).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(ADMIN_ONLY));
			mvc.perform(get("/api/company").with(member)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
		}
	}

	@Test
	void acceptedAdministratorCanUpdateCompany() throws Exception {
		String organization = organization();
		assertAdminSaves(current(organization, "admin_prefixed", "org:admin"), organization);
		// Session token v1 (flat org_id/org_role, no sts) is rejected before any role check (LLT-163, research R3).
		mvc.perform(put("/api/company").with(legacy(organization(), "admin_legacy", "admin"))
				.contentType(MediaType.APPLICATION_JSON).content(REGISTRATION)).andExpect(status().isUnauthorized());
		// No active organization: the security layer denies with the basic 403 body (ADR_032).
		mvc.perform(get("/api/company").with(TestTenants.withoutOrganization("user")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("forbidden"));
	}

	@Test
	void pendingListRevokeAndResendStayOutsideCore() throws Exception {
		RequestPostProcessor admin = current(organization(), "admin_a", "admin");
		RequestPostProcessor member = current(organization(), "member_a", "member");
		for (RequestPostProcessor actor : new RequestPostProcessor[] {admin, member}) {
			mvc.perform(get("/api/invitations").with(actor)).andExpect(status().isNotFound());
			mvc.perform(post("/api/invitations/revoke").with(actor)).andExpect(status().isNotFound());
		}
	}

	private void assertAdminSaves(RequestPostProcessor actor, String organization) throws Exception {
		mvc.perform(put("/api/company").with(actor).contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clerkOrgId").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
	}

	private RequestPostProcessor current(String organization, String user, String role) {
		return tenants().session(organization, user, role);
	}

	private RequestPostProcessor legacy(String organization, String user, String role) {
		return tenants().legacySession(organization, user, role);
	}

	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private String organization() {
		return "org_invite_" + UUID.randomUUID();
	}
}
