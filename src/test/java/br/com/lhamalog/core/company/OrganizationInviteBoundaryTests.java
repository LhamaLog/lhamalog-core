package br.com.lhamalog.core.company;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"management.otlp.metrics.export.enabled=false",
		"management.tracing.export.otlp.enabled=false"
})
@AutoConfigureMockMvc
class OrganizationInviteBoundaryTests {
	private static final String ADMIN_ONLY = "Somente o administrador da empresa pode alterar este cadastro.";
	private static final String SELECT_COMPANY = "Selecione uma empresa para continuar.";
	private static final String REGISTRATION = """
			{"data":{"legalName":"Empresa Convidada","document":""},"context":{"description":"Aceite"},"address":{},"organizationId":"org_outra","version":null}
			""";
	private static final String[] INVITE_PATHS = {
			"/api/invitations", "/api/company/invitations", "/api/organization-invitations", "/api/invitations/accept"
	};

	@Autowired MockMvc mvc;

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
				.andExpect(jsonPath("$.id").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
		mvc.perform(get("/api/company").with(current(organization, "admin_a", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
		for (RequestPostProcessor member : new RequestPostProcessor[] {
				current(organization, "member_current", "member"),
				current(organization, "member_prefixed", "org:member"),
				legacy(organization, "member_legacy", "member"),
				legacy(organization, "member_legacy_prefixed", "org:member")}) {
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
		String legacyAdmin = organization();
		assertAdminSaves(legacy(legacyAdmin, "admin_legacy", "admin"), legacyAdmin);
		String legacyPrefixed = organization();
		assertAdminSaves(legacy(legacyPrefixed, "admin_legacy_prefixed", "org:admin"), legacyPrefixed);
		mvc.perform(get("/api/company").with(jwt().jwt(token -> token.subject("user"))))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(SELECT_COMPANY));
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
				.andExpect(jsonPath("$.id").value(organization))
				.andExpect(jsonPath("$.data.legalName").value("Empresa Convidada"));
	}

	private RequestPostProcessor current(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", organization, "rol", role)));
	}

	private RequestPostProcessor legacy(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("org_id", organization).claim("org_role", role));
	}

	private String organization() {
		return "org_invite_" + UUID.randomUUID();
	}
}
