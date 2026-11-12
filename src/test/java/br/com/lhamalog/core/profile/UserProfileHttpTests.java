package br.com.lhamalog.core.profile;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.TestcontainersConfiguration;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"management.otlp.metrics.export.enabled=false",
		"management.tracing.export.otlp.enabled=false"
})
@AutoConfigureMockMvc
class UserProfileHttpTests {
	private static final String SELECT_COMPANY = "Selecione uma empresa para continuar.";
	private static final String CONFLICT = "Os dados foram alterados. Recarregue antes de salvar novamente.";
	private static final String CHECK_FIELDS = "Confira os campos informados.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired UserProfileRepository profiles;
	@Autowired ApplicationContext context;

	@Test
	void savesAndReadsOwnProfile() throws Exception {
		String user = userId();
		RequestPostProcessor session = session(organization(), user, "member");
		mvc.perform(get("/api/user/profile").with(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value(""))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.version").value(nullValue()));
		mvc.perform(get("/api/user/profile").with(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(nullValue()));
		assertTrue(profiles.findById(user).isEmpty());

		MvcResult created = mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Souza", "pt-BR", "America/Sao_Paulo", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ana Souza"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo")).andReturn();
		long version = version(created);
		mvc.perform(get("/api/user/profile").with(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ana Souza"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.version").value(version));

		MvcResult updated = mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "en", "America/Sao_Paulo", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ana Lima"))
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo")).andReturn();
		long current = version(updated);
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Velha", "pt-BR", "Europe/Lisbon", version)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(CONFLICT));
		mvc.perform(get("/api/user/profile").with(session))
				.andExpect(jsonPath("$.displayName").value("Ana Lima"))
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.version").value(current));

		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("   ", "pt-BR", "America/Sao_Paulo", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CHECK_FIELDS))
				.andExpect(jsonPath("$.fieldErrors.displayName").value("Informe o nome."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("", "pt-BR", "America/Sao_Paulo", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.displayName").value("Informe o nome."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("A".repeat(201), "pt-BR", "America/Sao_Paulo", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.displayName").value("O texto excede o limite permitido."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "pt_BR", "America/Sao_Paulo", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.language").value("Informe um idioma válido."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "", "America/Sao_Paulo", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.language").value("Informe o idioma."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "pt-BR", "Nao/Existe", current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.timezone").value("Informe um fuso horário válido."));
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"displayName":"Ana Lima","language":"en","version":%d}
						""".formatted(current)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CHECK_FIELDS))
				.andExpect(jsonPath("$.fieldErrors.timezone").value("Confira o preenchimento deste campo."));
		mvc.perform(get("/api/user/profile").with(session))
				.andExpect(jsonPath("$.displayName").value("Ana Lima"))
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.version").value(current));

		MvcResult alias = mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "Português (Brasil)", "(UTC-03:00) Brasília", current)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo")).andReturn();
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana Lima", "en-US", "America/Sao_Paulo", version(alias))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.language").value("en-US"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
	}

	@Test
	void profilesStayWithTheSessionUser() throws Exception {
		String organization = organization();
		String ana = userId();
		String bruno = userId();
		RequestPostProcessor anaSession = session(organization, ana, "member");
		RequestPostProcessor brunoSession = session(organization, bruno, "admin");
		mvc.perform(put("/api/user/profile").with(anaSession).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Ana", "pt-BR", "America/Sao_Paulo", null))).andExpect(status().isOk());
		mvc.perform(put("/api/user/profile").with(brunoSession).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Bruno", "en", "Europe/Lisbon", null))).andExpect(status().isOk());
		mvc.perform(get("/api/user/profile").with(anaSession)).andExpect(jsonPath("$.displayName").value("Ana"));
		mvc.perform(get("/api/user/profile").with(brunoSession)).andExpect(jsonPath("$.displayName").value("Bruno"));

		long anaVersion = version(mvc.perform(get("/api/user/profile").with(anaSession)).andReturn());
		mvc.perform(put("/api/user/profile").with(anaSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"displayName":"Ana ainda","language":"pt-BR","timezone":"America/Sao_Paulo","version":%d,"userId":"%s","clerkUserId":"%s","organizationId":"org_outra","tenantId":"org_outra","id":"%s"}
						""".formatted(anaVersion, bruno, bruno, bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ana ainda"))
				.andExpect(jsonPath("$.userId").doesNotExist())
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.clerkUserId").doesNotExist())
				.andExpect(jsonPath("$.organizationId").doesNotExist())
				.andExpect(jsonPath("$.tenantId").doesNotExist());
		mvc.perform(get("/api/user/profile").with(brunoSession)).andExpect(jsonPath("$.displayName").value("Bruno"));
		assertTrue(profiles.findById(ana).isPresent());
		assertTrue(profiles.findById(bruno).isPresent());

		RequestPostProcessor anaOtherCompany = session(organization(), ana, "admin");
		mvc.perform(get("/api/user/profile").with(anaOtherCompany))
				.andExpect(jsonPath("$.displayName").value("Ana ainda"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
	}

	@Test
	void memberCanUpdateOwnProfile() throws Exception {
		RequestPostProcessor member = session(organization(), userId(), "member");
		mvc.perform(get("/api/user/profile").with(member)).andExpect(status().isOk());
		mvc.perform(put("/api/user/profile").with(member).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Membro", "pt-BR", "America/Sao_Paulo", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Membro"));
	}

	@Test
	void sessionRulesStayDistinct() throws Exception {
		String user = userId();
		RequestPostProcessor session = session(organization(), user, "member");
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Sessao", "pt-BR", "America/Sao_Paulo", null))).andExpect(status().isOk());
		mvc.perform(get("/api/user/profile")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
				.content(profile("Invasao", "en", "UTC", null))).andExpect(status().isUnauthorized());
		RequestPostProcessor noCompany = jwt().jwt(token -> token.subject(user));
		mvc.perform(put("/api/user/profile").with(noCompany).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Sem empresa", "en", "UTC", 0L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SELECT_COMPANY));
		mvc.perform(get("/api/user/profile").with(noCompany)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SELECT_COMPANY));
		mvc.perform(get("/api/user/profile").with(session))
				.andExpect(jsonPath("$.displayName").value("Sessao"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
	}

	@Test
	void nameIsStoredWithoutIdentityCall() throws Exception {
		assertCoreDoesNotCallClerk();
		RequestPostProcessor session = session(organization(), userId(), "member");
		mvc.perform(put("/api/user/profile").with(session).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Nome Novo", "pt-BR", "America/Sao_Paulo", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Nome Novo"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
		mvc.perform(get("/api/user/profile").with(session)).andExpect(jsonPath("$.displayName").value("Nome Novo"));
		mvc.perform(get("/api/user/profile").with(session)).andExpect(jsonPath("$.displayName").value("Nome Novo"))
				.andExpect(jsonPath("$.language").value("pt-BR"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
	}

	@Test
	void profileDoesNotChangeCompany() throws Exception {
		String organization = organization();
		RequestPostProcessor admin = session(organization, userId(), "admin");
		mvc.perform(put("/api/company").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(company("America/Sao_Paulo", "pt-BR"))).andExpect(status().isOk());
		mvc.perform(put("/api/user/profile").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content(profile("Pessoa", "en", "Europe/Lisbon", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
		mvc.perform(get("/api/user/profile").with(admin))
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
		mvc.perform(get("/api/company").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.preferences.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.settings.data.timeZone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.preferences.language").value("pt-BR"))
				.andExpect(jsonPath("$.context.language").value("pt-BR"))
				.andExpect(jsonPath("$.settings.data.language").value("pt-BR"));
	}

	private void assertCoreDoesNotCallClerk() throws Exception {
		String properties;
		try (var input = getClass().getResourceAsStream("/application.properties")) {
			properties = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertFalse(properties.contains("CLERK_SECRET_KEY"));
		assertFalse(properties.contains("clerk.secret"));
		for (var constructor : UserProfileService.class.getDeclaredConstructors()) {
			for (Class<?> parameter : constructor.getParameterTypes()) {
				String name = parameter.getName().toLowerCase();
				assertFalse(name.contains("clerk") || name.contains("restclient") || name.contains("webclient"), name);
			}
		}
		for (String bean : context.getBeanDefinitionNames()) {
			Class<?> type = context.getType(bean);
			if (type == null) {
				continue;
			}
			String name = type.getName().toLowerCase();
			assertFalse(name.contains("clerk") && (name.contains("client") || name.contains("backend")), name);
		}
	}

	private long version(MvcResult result) throws Exception {
		return mapper.readTree(result.getResponse().getContentAsString()).get("version").asLong();
	}

	private static String profile(String displayName, String language, String timezone, Long version) {
		String versionJson = version == null ? "null" : Long.toString(version);
		return """
				{"displayName":"%s","language":"%s","timezone":"%s","version":%s}
				""".formatted(displayName, language, timezone, versionJson);
	}

	private static String company(String timezone, String language) {
		return """
				{"data":{"legalName":"Empresa"},"context":{},"address":{},"preferences":{"timezone":"%s","language":"%s"},"settings":{"data":{"legalName":"Empresa","timeZone":"%s","language":"%s"},"context":{},"address":{},"preferences":{}},"version":null}
				""".formatted(timezone, language, timezone, language);
	}

	private RequestPostProcessor session(String organization, String user, String role) {
		return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", organization, "rol", role)));
	}

	private static String organization() {
		return "org_profile_" + UUID.randomUUID();
	}

	private static String userId() {
		return "user_profile_" + UUID.randomUUID();
	}
}
