package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.Finding;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;
import br.com.lhamalog.core.occurrence.application.OccurrenceMerge;

/** Spec 013, research R4: how a finding becomes the occurrence to write. */
class OccurrenceMergeTest {

	private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

	private static Finding finding(List<String> lines, String title, Map<String, String> fields, Boolean sensitive) {
		return new Finding(new OccurrenceKey("ERROR|falha|svc|h", "ERROR"), "host-2", 13,
				Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-25T11:00:00Z"), true, lines, null, title,
				null, null, fields, sensitive, null, null, null);
	}

	private static Document merge(Document before, Finding finding) {
		return OccurrenceMerge.merge(before, finding, "tenant-1", "product-1", "regras-v2", NOW, "rev-new", 20);
	}

	private static Document stored(String status) {
		return new Document("_id", "occ-1").append("tenantId", "tenant-1")
			.append("productId", "product-1")
			.append("signature", "ERROR|falha|svc|h")
			.append("type", "ERROR")
			.append("status", status)
			.append("count", 10L)
			.append("title", "Título anterior")
			.append("summary", "Resumo anterior")
			.append("traceId", "trace-antigo")
			.append("extractedFields", new Document("antigo", "1"))
			.append("sensitiveData", false)
			.append("samples", new ArrayList<>(List.of("a", "b")))
			.append("statusHistory", new ArrayList<>(List.of(new Document("from", null).append("to", "ABERTA"))))
			.append("revision", "rev-old");
	}

	@Test
	void createsAnOpenOccurrenceWithItsCreationInTheHistory() {
		Document after = merge(null, finding(List.of("l1"), "Falha", Map.of("http.route", "/x"), true));
		assertThat(after.getString("_id")).isNotBlank();
		assertThat(after.getString("status")).isEqualTo("ABERTA");
		assertThat(after.get("count")).isEqualTo(13L);
		assertThat(after.get("firstSeenAt")).isEqualTo(Date.from(Instant.parse("2026-11-20T10:00:00Z")));
		assertThat(after.getBoolean("recurring")).isTrue();
		assertThat(after.getString("rulesVersion")).isEqualTo("regras-v2");
		assertThat(after.getString("revision")).isEqualTo("rev-new");
		assertThat(after.get("extractedFields", Document.class)).containsEntry("http.route", "/x");
		assertThat(after.getList("samples", String.class)).containsExactly("l1");
		List<Document> history = after.getList("statusHistory", Document.class);
		assertThat(history).hasSize(1);
		assertThat(history.getFirst().get("from")).isNull();
		assertThat(history.getFirst().getString("to")).isEqualTo("ABERTA");
		assertThat(history.getFirst().get("changedBy", Document.class))
			.containsEntry("type", "SYSTEM")
			.containsEntry("id", "modulo-ocorrencia");
	}

	@Test
	void closedOccurrenceReopensWithTheChangeInTheHistory() {
		Document after = merge(stored("FECHADA"), finding(List.of(), null, null, null));
		assertThat(after.getString("status")).isEqualTo("ABERTA");
		List<Document> history = after.getList("statusHistory", Document.class);
		assertThat(history).hasSize(2);
		assertThat(history.getLast().getString("from")).isEqualTo("FECHADA");
		assertThat(history.getLast().get("changedAt")).isEqualTo(Date.from(NOW));
	}

	@Test
	void openAndInAnalysisKeepStatusAndHistory() {
		for (String status : List.of("ABERTA", "EM_ANALISE")) {
			Document after = merge(stored(status), finding(List.of(), null, null, null));
			assertThat(after.getString("status")).isEqualTo(status);
			assertThat(after.getList("statusHistory", Document.class)).hasSize(1);
		}
	}

	@Test
	void unknownStatusBecomesOpen() {
		Document after = merge(stored("RESOLVIDA"), finding(List.of(), null, null, null));
		assertThat(after.getString("status")).isEqualTo("ABERTA");
		assertThat(after.getList("statusHistory", Document.class).getLast().getString("from")).isEqualTo("RESOLVIDA");
	}

	@Test
	void nullFieldsKeepThePreviousValuesAndSentOnesReplaceThem() {
		Document kept = merge(stored("ABERTA"), finding(List.of(), null, null, null));
		assertThat(kept.getString("title")).isEqualTo("Título anterior");
		assertThat(kept.getString("summary")).isEqualTo("Resumo anterior");
		assertThat(kept.getString("traceId")).isEqualTo("trace-antigo");
		assertThat(kept.get("extractedFields", Document.class)).containsEntry("antigo", "1");
		assertThat(kept.getBoolean("sensitiveData")).isFalse();
		assertThat(kept.getList("samples", String.class)).containsExactly("a", "b");
		assertThat(kept.getString("hostName")).isEqualTo("host-2");
		assertThat(kept.getString("_id")).isEqualTo("occ-1");

		Document replaced = merge(stored("ABERTA"), finding(List.of("c"), "Novo", Map.of("http.route", "/y"), true));
		assertThat(replaced.getString("title")).isEqualTo("Novo");
		assertThat(replaced.get("extractedFields", Document.class)).containsOnlyKeys("http.route");
		assertThat(replaced.getBoolean("sensitiveData")).isTrue();
		assertThat(replaced.getList("samples", String.class)).containsExactly("a", "b", "c");
		assertThat(replaced.get("count")).isEqualTo(13L);
	}

	@Test
	void keepsOnlyTheNewestSamples() {
		Document before = stored("ABERTA");
		before.put("samples", new ArrayList<>(IntStream.rangeClosed(1, 18).mapToObj(i -> "old-" + i).toList()));
		Document after = merge(before, finding(List.of("new-1", "new-2", "new-3", "new-4"), null, null, null));
		List<String> samples = after.getList("samples", String.class);
		assertThat(samples).hasSize(20);
		assertThat(samples.getFirst()).isEqualTo("old-3");
		assertThat(samples.getLast()).isEqualTo("new-4");
	}

	@Test
	void doesNotChangeTheStoredDocument() {
		Document before = stored("FECHADA");
		merge(before, finding(List.of("z"), "Novo", null, null));
		assertThat(before.getString("status")).isEqualTo("FECHADA");
		assertThat(before.getList("samples", String.class)).containsExactly("a", "b");
		assertThat(before.getList("statusHistory", Document.class)).hasSize(1);
		assertThat(before.getString("revision")).isEqualTo("rev-old");
	}

}
