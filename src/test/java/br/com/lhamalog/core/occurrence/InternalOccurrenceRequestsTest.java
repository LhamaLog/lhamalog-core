package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.InternalModuleWorld.Company;
import br.com.lhamalog.core.occurrence.InternalModuleWorld.Finding;
import br.com.lhamalog.core.occurrence.InternalModuleWorld.Summary;
import br.com.lhamalog.core.occurrence.api.InternalOccurrenceRequests;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary;
import br.com.lhamalog.core.occurrence.application.InternalApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 013, research R5: the strict reader of the contract bodies. */
class InternalOccurrenceRequestsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Company COMPANY = new Company("org", "tenant-1", "product-1", null);

	private static JsonNode tree(Object body) {
		return JSON.valueToTree(body);
	}

	private static Summary valid() {
		return InternalModuleWorld.summary(COMPANY)
			.exec("V0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11")
			.set("versaoCatalogo", "catalogo-3")
			.set("identificacaoRegras", Map.of("versaoUnica", "regras-v1", "somenteNivel", false, "catalogoEntrou", true))
			.finding(InternalModuleWorld.finding("ERROR|falha|svc|h")
				.revision("rev-1")
				.set("titulo", "Falha")
				.set("resumo", "Resumo")
				.set("numeroLinha", 42)
				.set("camposExtraido", Map.of("http.route", "/api/x"))
				.set("dadosSensiveis", true)
				.set("traceId", "trace-1")
				.set("correlationId", "corr-1")
				.set("moduloId", "mod-1"))
			.finding(InternalModuleWorld.finding("WARNING|lento|svc|h").type("WARNING").set("linhas", List.of()));
	}

	@Test
	void readsACompleteSummary() {
		AnalysisSummary summary = InternalOccurrenceRequests.readSummary(tree(valid().build()), 1000);
		assertThat(summary.key().tenantId()).isEqualTo("tenant-1");
		assertThat(summary.key().execId()).isEqualTo("V0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11");
		assertThat(summary.rulesVersion()).isEqualTo("regras-v1");
		assertThat(summary.findings()).hasSize(2);
		AnalysisSummary.Finding first = summary.findings().getFirst();
		assertThat(first.key()).isEqualTo(new AnalysisSummary.OccurrenceKey("ERROR|falha|svc|h", "ERROR"));
		assertThat(first.count()).isEqualTo(3);
		assertThat(first.firstSeenAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
		assertThat(first.consultedRevision()).isEqualTo("rev-1");
		assertThat(first.lineNumber()).isEqualTo(42);
		assertThat(first.extractedFields()).containsEntry("http.route", "/api/x");
		assertThat(first.sensitiveData()).isTrue();
		assertThat(first.moduleId()).isEqualTo("mod-1");
		AnalysisSummary.Finding second = summary.findings().get(1);
		assertThat(second.consultedRevision()).isNull();
		assertThat(second.lines()).isEmpty();
		assertThat(second.title()).isNull();
		assertThat(second.extractedFields()).isNull();
	}

	@Test
	void eachContractRuleIsDadosInvalidos() {
		List<Consumer<Summary>> broken = List.of(
				s -> s.set("outro", 1),
				s -> s.set("execId", "nao-e-exec"),
				s -> s.set("versaoRegras", ""),
				s -> s.set("contadores", Map.of("linhasProcessadas", 9, "erros", 1, "warnings", 1, "informacao", 0,
						"dadosSensiveisMascarados", 0)),
				s -> s.set("identificacaoRegras", Map.of("versaoUnica", "outra", "somenteNivel", false,
						"catalogoEntrou", true)),
				s -> s.finding(InternalModuleWorld.finding("ERROR|falha|svc|h")),
				s -> s.finding(InternalModuleWorld.finding("a").type("FATAL")),
				s -> s.finding(InternalModuleWorld.finding("b").count(0)),
				s -> s.finding(InternalModuleWorld.finding("c").set("primeiraOcorrencia", "2026-11-20T10:00:00-03:00")),
				s -> s.finding(InternalModuleWorld.finding("d").seen(Instant.parse("2026-11-21T00:00:00Z"),
						Instant.parse("2026-11-20T00:00:00Z"))),
				s -> s.finding(InternalModuleWorld.finding("e").set("numeroLinha", 0)),
				s -> s.finding(InternalModuleWorld.finding("f").set("revisaoConsultada", "")),
				s -> s.finding(InternalModuleWorld.finding("g").without("titulo")),
				s -> s.finding(InternalModuleWorld.finding("h").set("linhas", List.of(1, 2))),
				s -> s.finding(InternalModuleWorld.finding("i").set("camposExtraido", Map.of("k", 1))),
				s -> s.finding(InternalModuleWorld.finding("j").set("recorrente", "sim")),
				s -> s.finding(InternalModuleWorld.finding("k").set("contagem", "3")),
				s -> s.finding(InternalModuleWorld.finding("l").set("traceId", null)),
				s -> s.finding(InternalModuleWorld.finding("m").set("extra", true)));
		for (Consumer<Summary> change : broken) {
			Summary summary = valid();
			change.accept(summary);
			assertThatThrownBy(() -> InternalOccurrenceRequests.readSummary(tree(summary.build()), 1000))
				.isInstanceOfSatisfying(InternalApiException.class,
						refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_DATA));
		}
	}

	@Test
	void tooManyFindingsHaveTheirOwnCode() {
		Summary summary = InternalModuleWorld.summary(COMPANY);
		for (int i = 0; i < 4; i++) summary.finding(new Finding("ERROR|n" + i + "|svc|h"));
		assertThatThrownBy(() -> InternalOccurrenceRequests.readSummary(tree(summary.build()), 3))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.SUMMARY_TOO_LARGE));
	}

	@Test
	void nonObjectRootIsRequisicaoInvalida() {
		assertThatThrownBy(() -> InternalOccurrenceRequests.readSummary(tree(List.of()), 10))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_REQUEST));
		assertThatThrownBy(() -> InternalOccurrenceRequests.readLookup(tree("texto")))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_REQUEST));
	}

	@Test
	void summaryWithoutFindingsIsValid() {
		AnalysisSummary summary = InternalOccurrenceRequests.readSummary(tree(InternalModuleWorld.summary(COMPANY).build()), 10);
		assertThat(summary.findings()).isEmpty();
		assertThat(summary.key().execId()).isNull();
	}

}
