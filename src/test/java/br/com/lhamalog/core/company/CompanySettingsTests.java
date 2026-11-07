package br.com.lhamalog.core.company;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.company.CompanyResponses.CompanyResponse;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CompanySettingsTests {
	private final JsonMapper mapper = new JsonMapper();

	@Test
	void legacySaveRemainsPartialAndPreservesFieldsRemovedFromTheScreen() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		CompanyService service = service(stored);
		service.save("org_a", "user_a", json("""
				{"data":{"legalName":"Empresa A","tradeName":"A","document":"","size":"","registrationDate":null},
				 "context":{"sector":"Tecnologia","mission":"Manter","targetAudience":"","differentiators":"","language":""},
				 "address":{"street":"Rua A","number":"1"},"version":null}
				"""));
		CompanyResponse reloaded = service.save("org_a", "user_a", json("""
				{"data":{"legalName":"Empresa A"},"context":{"sector":"Tecnologia"},"address":{"street":"Rua A"},
				 "version":0}
				"""));
		assertEquals("Manter", reloaded.context().mission());
		assertEquals("Empresa A", reloaded.data().legalName());
		assertEquals("org_a", reloaded.clerkOrgId());
		assertNull(reloaded.summary().metrics());
		assertNull(reloaded.summary().ingestion());
		assertFalse(reloaded.summary().verified());
		assertEquals("unavailable", reloaded.summary().verification());
		assertNotEquals(85, reloaded.summary().completion().percent());
	}

	@Test
	void settingsRoundTripMatchesTheWebConfirmation() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		AtomicReference<CompanyLogo> logo = new AtomicReference<>();
		CompanyService service = serviceRememberingLogo(stored, logo);
		String png = java.util.Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0});
		String body = """
				{"id":"org_evil","clerkOrgId":"org_evil","preserved":{"historical":true},
				 "data":{"legalName":"LhamaLog Tecnologia LTDA","tradeName":"LhamaLog","document":"11.444.777/0001-61",
				   "registrationDate":"2020-01-02","size":"11 - 50 colaboradores"},
				 "context":{"sector":"Tecnologia / SaaS","mission":"Monitoramento","targetAudience":"Times de operação",
				   "differentiators":"Vários serviços","language":"Português (Brasil)"},
				 "address":{"street":"Avenida Brasil","number":"1234","complement":"Sala 502","district":"Centro",
				   "city":"Maringá","state":"Paraná","country":"Brasil","postalCode":"87000-000"},
				 "settings":{"organizationId":"org_evil","version":null,
				   "data":{"legalName":"LhamaLog Tecnologia LTDA","tradeName":"LhamaLog","document":"11.444.777/0001-61",
				     "email":"contato@example.com","stateRegistration":"123","sector":"Tecnologia / SaaS",
				     "description":"Monitoramento","website":"https://example.com","size":"11 - 50 colaboradores",
				     "timeZone":"(UTC-03:00) Brasília","language":"Português (Brasil)",
				     "logo":{"fileName":"logo.png","mediaType":"image/png","dataBase64":"%s"}},
				   "context":{"environmentTypes":["Produção","Homologação"],"monitoredSystems":["API","Backend"],
				     "criticalWindows":"Horário comercial e deploys","description":"Vários serviços"},
				   "address":{"postalCode":"87000-000","country":"Brasil","street":"Avenida Brasil","number":"1234",
				     "complement":"Sala 502","district":"Centro","city":"Maringá","state":"Paraná","reference":"Praça",
				     "fiscalAddress":true},
				   "preferences":{"currency":"Real (BRL)","dateFormat":"dd/mm/aaaa","timeFormat":"24 horas",
				     "dailySummary":true,"autoSuggestions":false,
				     "telemetry":{"interval":"Últimas 24 horas","initialEnvironment":"Todos os ambientes",
				       "logOrder":"Mais recentes primeiro","activeProductsOnly":true},
				     "ai":{"detailLevel":"Alto","tone":"Objetivo e técnico","priority":"Criticidade",
				       "probableCause":true,"correctiveAction":true,"relateSimilar":false},
				     "notifications":{"criticalImmediate":true,"recurringFailures":false},
				     "security":{"requireMfa":true,"approvedIntegrations":false,"maskSensitive":true}}},
				 "version":null}
				""".formatted(png);
		CompanyResponse saved = service.save("org_a", "user_a", json(body));
		assertEquals("org_a", saved.organizationId());
		assertEquals("2020-01-02", saved.data().registrationDate().toString());
		assertEquals("Times de operação", saved.context().targetAudience());
		assertTrue(saved.limitations().stream().filter(item -> item.code().equals("MANAGER_MFA")).allMatch(item -> !item.enforced()));
		assertTrue(webWouldConfirm(json(body).get("settings"), mapper.valueToTree(saved)));
		assertTrue(webWouldConfirm(json(body).get("settings"), mapper.valueToTree(service.get("org_a"))));
		assertNotEquals("org_evil", saved.id());
	}

	@Test
	void rejectsInvalidCnpjMismatchedSectorAndStaleVersion() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		CompanyService service = service(stored);
		FieldValidationException invalid = assertThrows(FieldValidationException.class, () -> service.save("org_a", "user",
				json("""
						{"data":{"legalName":"A","document":"11.444.777/0001-00","email":"ruim","sector":"Um"},
						 "context":{"sector":"Outro"},"address":{},"version":null}
						""")));
		assertEquals("Informe um CNPJ válido.", invalid.fieldErrors().get("data.document"));
		assertTrue(invalid.fieldErrors().containsKey("data.sector"));
		service.save("org_a", "user", json("""
				{"data":{"legalName":"A","document":"","size":""},"context":{"sector":"Tecnologia"},
				 "address":{"street":"Rua"},"version":null}
				"""));
		assertThrows(OptimisticLockingFailureException.class, () -> service.save("org_a", "user", json("""
				{"data":{},"context":{},"address":{},"version":null}
				""")));
	}

	@Test
	void incompleteSettingsAreStoredAndLegacyPartialAddressRemainsAccepted() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		CompanyService service = service(stored);
		CompanyResponse saved = service.save("org_a", "user", json("""
				{"data":{"legalName":"Empresa","registrationDate":"2020-01-02"},"context":{"mission":"Antiga"},
				 "address":{"street":"Rua"},"settings":{"data":{"legalName":"Empresa","email":""},"context":{},
				 "address":{"reference":"Portaria","fiscalAddress":false},"preferences":{}},"version":null}
				"""));
		assertEquals("", saved.settings().data().email());
		assertEquals("Portaria", saved.settings().address().reference());
		assertEquals("Antiga", service.get("org_a").context().mission());
		assertEquals("2020-01-02", service.get("org_a").data().registrationDate().toString());
		assertDoesNotThrow(() -> service.save("org_a", "user", json("""
				{"data":{"legalName":"Empresa","document":""},"context":{"sector":""},"address":{"street":"Rua"},"version":0}
				""")));
	}

	@Test
	void logoIsSanitizedAndReadOnlyForTheSessionTenant() {
		AtomicReference<Company> stored = new AtomicReference<>();
		AtomicReference<CompanyLogo> logo = new AtomicReference<>();
		CompanyRepository companies = mock(CompanyRepository.class);
		CompanyLogoRepository logos = mock(CompanyLogoRepository.class);
		when(companies.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				stored.get() != null && stored.get().id().equals(invocation.getArgument(0)) ? stored.get() : null));
		when(companies.save(any())).thenAnswer(invocation -> persist(stored, invocation.getArgument(0)));
		when(logos.save(any())).thenAnswer(invocation -> {
			logo.set(invocation.getArgument(0));
			return logo.get();
		});
		when(logos.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				logo.get() != null && logo.get().id().equals(invocation.getArgument(0)) ? logo.get() : null));
		CompanyService service = new CompanyService(companies, logos);
		byte[] svg = """
				<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><rect width="1" height="1" onload="alert(1)"/></svg>
				""".getBytes(StandardCharsets.UTF_8);
		CompanyResponse saved = service.saveLogo("org_a", "user_a", null, "../logo.svg", svg);
		assertEquals("image/svg+xml", saved.logo().contentType());
		assertFalse(new String(logo.get().content(), StandardCharsets.UTF_8).toLowerCase().contains("<script"));
		assertFalse(new String(logo.get().content(), StandardCharsets.UTF_8).toLowerCase().contains("onload"));
		assertThrows(ResponseStatusException.class, () -> service.logo("org_b"));
		assertArrayEquals(logo.get().content(), service.logo("org_a").content());
	}

	@Test
	void rejectsHtmlAndOversizedLogo() {
		assertThrows(FieldValidationException.class,
				() -> LogoFiles.inspect("<html></html>".getBytes(StandardCharsets.UTF_8), "logo.svg"));
		byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0};
		assertEquals("image/png", LogoFiles.inspect(png, "foto.png").contentType());
		assertThrows(FieldValidationException.class, () -> LogoFiles.inspect(new byte[LogoFiles.MAX_BYTES + 1], "a.png"));
	}

	@Test
	void unavailableActionsStayHonest() {
		CompanySessionResolver resolver = mock(CompanySessionResolver.class);
		when(resolver.resolve(any())).thenReturn(new CompanySession("org_a", "user_a", false));
		CompanyController controller = new CompanyController(mock(CompanyService.class), resolver);
		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.exportPdf(null));
		assertEquals(HttpStatus.NOT_IMPLEMENTED, error.getStatusCode());
		assertFalse(error.getReason().toLowerCase().contains("sucesso"));
		assertEquals(HttpStatus.NOT_IMPLEMENTED, assertThrows(ResponseStatusException.class,
				() -> controller.sync(null)).getStatusCode());
		assertEquals(HttpStatus.NOT_IMPLEMENTED, assertThrows(ResponseStatusException.class,
				() -> controller.postalCode(null, "87000000")).getStatusCode());
	}

	private static boolean webWouldConfirm(JsonNode sent, JsonNode response) {
		JsonNode echoed = response.get("settings");
		if (echoed == null || !echoed.isObject() || echoed.isEmpty()) {
			return false;
		}
		return canonical(sent).equals(canonical(echoed));
	}

	private static String canonical(JsonNode settings) {
		tools.jackson.databind.node.ObjectNode copy = (tools.jackson.databind.node.ObjectNode) settings.deepCopy();
		copy.remove("version");
		copy.remove("organizationId");
		sort(copy.path("context"), "environmentTypes");
		sort(copy.path("context"), "monitoredSystems");
		return copy.toString();
	}

	private static void sort(JsonNode node, String field) {
		if (!(node instanceof tools.jackson.databind.node.ObjectNode object)) {
			return;
		}
		JsonNode array = object.get(field);
		if (array == null || !array.isArray()) {
			return;
		}
		java.util.List<String> values = new java.util.ArrayList<>();
		array.forEach(item -> values.add(item.asText()));
		java.util.Collections.sort(values);
		var sorted = tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
		values.forEach(sorted::add);
		object.set(field, sorted);
	}

	private CompanyService serviceRememberingLogo(AtomicReference<Company> stored, AtomicReference<CompanyLogo> logo) {
		CompanyRepository companies = mock(CompanyRepository.class);
		CompanyLogoRepository logos = mock(CompanyLogoRepository.class);
		when(companies.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				stored.get() != null && stored.get().id().equals(invocation.getArgument(0)) ? stored.get() : null));
		when(companies.save(any())).thenAnswer(invocation -> persist(stored, invocation.getArgument(0)));
		when(logos.save(any())).thenAnswer(invocation -> {
			logo.set(invocation.getArgument(0));
			return logo.get();
		});
		when(logos.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				logo.get() != null && logo.get().id().equals(invocation.getArgument(0)) ? logo.get() : null));
		return new CompanyService(companies, logos);
	}

	private CompanyService service(AtomicReference<Company> stored) {
		CompanyRepository companies = mock(CompanyRepository.class);
		when(companies.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				stored.get() != null && stored.get().id().equals(invocation.getArgument(0)) ? stored.get() : null));
		when(companies.save(any())).thenAnswer(invocation -> persist(stored, invocation.getArgument(0)));
		return new CompanyService(companies, mock(CompanyLogoRepository.class));
	}

	private static Company persist(AtomicReference<Company> stored, Company company) {
		Long version = company.version() == null ? 0L : company.version() + 1;
		Company persisted = new Company(company.id(), company.clerkOrgId(), company.data(), company.context(),
				company.address(), company.preferences(), company.settings(), company.logo(), company.updatedAt(),
				company.updatedByUserId(), version);
		stored.set(persisted);
		return persisted;
	}

	private JsonNode json(String body) {
		return mapper.readTree(body);
	}
}
