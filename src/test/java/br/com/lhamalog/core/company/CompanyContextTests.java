package br.com.lhamalog.core.company;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CompanyContextTests {
	private final JsonMapper mapper = new JsonMapper();

	@Test
	void contextDescriptionAcceptsTheLimitAndRejectsOneMoreCharacterOnBothPaths() throws Exception {
		String atLimit = "a".repeat(CompanyContextLimits.DESCRIPTION_MAX);
		String overLimit = "a".repeat(CompanyContextLimits.DESCRIPTION_MAX + 1);
		CompanyService service = service(new AtomicReference<>());
		assertDoesNotThrow(() -> service.save("org_a", "user_a", json("""
				{"data":{},"context":{"description":"%s"},"address":{},"version":null}
				""".formatted(atLimit))));
		FieldValidationException legacy = assertThrows(FieldValidationException.class, () -> service.save("org_b", "user_b",
				json("""
						{"data":{},"context":{"description":"%s"},"address":{},"version":null}
						""".formatted(overLimit))));
		assertEquals("O texto excede o limite permitido.", legacy.fieldErrors().get("context.description"));
		FieldValidationException settings = assertThrows(FieldValidationException.class, () -> service.save("org_c", "user_c",
				json("""
						{"data":{},"context":{},"address":{},"settings":{"context":{"description":"%s"}},"version":null}
						""".formatted(overLimit))));
		assertEquals("O texto excede o limite permitido.", settings.fieldErrors().get("settings.context.description"));
	}

	@Test
	void revisionsRecordContextChangesOnlyAndStayInsideTheTenant() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		CompanyService service = service(stored);
		service.save("org_a", "user_a", json("""
				{"data":{"legalName":"Empresa"},"context":{"description":"Forma inicial","sector":"Tecnologia / SaaS",
				 "environments":["Produção"],"monitoredSystems":["API"],"criticalWindows":"Horário comercial"},
				 "address":{},"version":null}
				"""));
		assertEquals(1, service.contextRevisions("org_a").size());
		service.save("org_a", "user_a", json("""
				{"data":{"legalName":"Empresa alterada"},"context":{},"address":{},"version":0}
				"""));
		assertEquals(1, service.contextRevisions("org_a").size());
		service.save("org_a", "user_b", json("""
				{"data":{},"context":{"description":"Forma revista"},"address":{},"version":1}
				"""));
		List<CompanyContextRevision> history = service.contextRevisions("org_a");
		assertEquals(List.of(2L, 1L), history.stream().map(CompanyContextRevision::revision).toList());
		assertEquals("user_b", history.get(0).actorUserId());
		assertEquals("Forma revista", history.get(0).description());
		assertEquals("Tecnologia / SaaS", history.get(0).sector());
		assertEquals("user_a", history.get(1).actorUserId());
		assertEquals("Forma inicial", service.contextRevision("org_a", 1).description());
		assertTrue(service.contextRevisions("org_b").isEmpty());
		ResponseStatusException missing = assertThrows(ResponseStatusException.class,
				() -> service.contextRevision("org_b", 1));
		assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
	}

	@Test
	void promptContainsThePersistedContextAndDoesNotClaimExecution() throws Exception {
		AtomicReference<Company> stored = new AtomicReference<>();
		CompanyService service = service(stored);
		service.save("org_a", "user_a", json("""
				{"data":{},"context":{},"address":{},"settings":{"data":{"sector":"Tecnologia / SaaS"},
				 "context":{"environmentTypes":["Produção"],"monitoredSystems":["Backend"],
				 "criticalWindows":"Horário comercial","description":"Atua com monitoramento de APIs"}},"version":null}
				"""));
		CompanyContextPrompt prompt = service.contextPrompt("org_a");
		assertFalse(prompt.executed());
		assertTrue(prompt.prompt().contains("Tecnologia / SaaS"));
		assertTrue(prompt.prompt().contains("Produção"));
		assertTrue(prompt.prompt().contains("Backend"));
		assertTrue(prompt.prompt().contains("Horário comercial"));
		assertTrue(prompt.prompt().contains("Atua com monitoramento de APIs"));
		Company base = Company.empty("org_long");
		stored.set(new Company(base.id(), base.clerkOrgId(), base.data(),
				new Company.Context("", "", "", "", "", List.of(), "", List.of(), "B".repeat(2500)), base.address(),
				base.preferences(), base.settings(), base.logo(), base.updatedAt(), base.updatedByUserId(), base.version()));
		String bounded = service.contextPrompt("org_long").prompt();
		assertTrue(bounded.contains("B".repeat(CompanyContextLimits.DESCRIPTION_MAX)));
		assertFalse(bounded.contains("B".repeat(CompanyContextLimits.DESCRIPTION_MAX + 1)));
	}

	private CompanyService service(AtomicReference<Company> stored) {
		CompanyRepository companies = mock(CompanyRepository.class);
		CompanyContextRevisionRepository revisions = mock(CompanyContextRevisionRepository.class);
		List<CompanyContextRevision> saved = new ArrayList<>();
		when(companies.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				stored.get() != null && stored.get().id().equals(invocation.getArgument(0)) ? stored.get() : null));
		when(companies.save(any())).thenAnswer(invocation -> persist(stored, invocation.getArgument(0)));
		when(revisions.save(any())).thenAnswer(invocation -> {
			CompanyContextRevision revision = invocation.getArgument(0);
			saved.add(revision);
			return revision;
		});
		when(revisions.findByTenantIdOrderByRevisionDesc(any())).thenAnswer(invocation -> saved.stream()
				.filter(revision -> revision.tenantId().equals(invocation.getArgument(0)))
				.sorted(Comparator.comparingLong(CompanyContextRevision::revision).reversed()).toList());
		when(revisions.findByTenantIdAndRevision(any(), anyLong())).thenAnswer(invocation -> saved.stream()
				.filter(revision -> revision.tenantId().equals(invocation.getArgument(0))
						&& revision.revision() == (Long) invocation.getArgument(1))
				.findFirst());
		return new CompanyService(companies, mock(CompanyLogoRepository.class), revisions);
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
