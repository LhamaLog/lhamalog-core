package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.Finding;

/**
 * Turns the stored occurrence and one finding into the occurrence to write (spec 013, research R4; rules 1 to 9 of
 * {@code specs/012-consultar-ocorrencias/contracts/ocorrencia-documento.md}). Pure: no database, no clock.
 */
public final class OccurrenceMerge {

	public static final String SYSTEM_AUTHOR = "modulo-ocorrencia";

	static final String OPEN = "ABERTA";

	static final String CLOSED = "FECHADA";

	static final String IN_ANALYSIS = "EM_ANALISE";

	private OccurrenceMerge() {
	}

	/**
	 * @param before stored occurrence, or null when the finding creates it
	 * @return a new document; {@code before} is not changed
	 */
	public static Document merge(Document before, Finding finding, String tenantId, String productId,
			String rulesVersion, Instant now, String newRevision, int maxSamples) {
		Document after = before == null ? new Document("_id", UUID.randomUUID().toString()) : new Document(before);
		after.put("tenantId", tenantId);
		after.put("productId", productId);
		after.put("signature", finding.key().signature());
		after.put("type", finding.key().type());
		after.put("gravidade", finding.key().type());
		after.put("count", finding.count());
		after.put("firstSeenAt", Date.from(finding.firstSeenAt()));
		after.put("lastSeenAt", Date.from(finding.lastSeenAt()));
		after.put("recurring", finding.recurring());
		after.put("rulesVersion", rulesVersion);
		after.put("hostName", finding.hostName());
		putIfPresent(after, "title", finding.title());
		putIfPresent(after, "summary", finding.summary());
		putIfPresent(after, "traceId", finding.traceId());
		putIfPresent(after, "correlationId", finding.correlationId());
		putIfPresent(after, "moduleId", finding.moduleId());
		putIfPresent(after, "lineNumber", finding.lineNumber());
		putIfPresent(after, "sensitiveData", finding.sensitiveData());
		if (finding.extractedFields() != null) after.put("extractedFields", new Document(finding.extractedFields()));
		after.put("samples", samples(before, finding.lines(), maxSamples));
		status(before, after, now);
		after.put("revision", newRevision);
		return after;
	}

	/** Previous samples plus the new lines at the end, keeping the newest {@code maxSamples}. */
	static List<String> samples(Document before, List<String> lines, int maxSamples) {
		List<String> samples = new ArrayList<>();
		if (before != null && before.get("samples") instanceof List<?> previous) {
			previous.stream().filter(String.class::isInstance).map(String.class::cast).forEach(samples::add);
		}
		samples.addAll(lines);
		return samples.size() <= maxSamples ? samples
				: new ArrayList<>(samples.subList(samples.size() - maxSamples, samples.size()));
	}

	/**
	 * Created as ABERTA; FECHADA (or an unknown status) reopens; ABERTA and EM_ANALISE stay. An archived occurrence is
	 * unarchived and reopened as ABERTA, so a problem that happens again is not hidden (spec 014, research R5).
	 */
	private static void status(Document before, Document after, Instant now) {
		List<Object> history = new ArrayList<>();
		if (before != null && before.get("statusHistory") instanceof List<?> previous) history.addAll(previous);
		Object current = before == null ? null : before.get("status");
		boolean archived = before != null && before.get("archivedAt") != null;
		after.remove("archivedAt");
		after.remove("archivedBy");
		boolean keeps = OPEN.equals(current) || (IN_ANALYSIS.equals(current) && !archived);
		if (!keeps) {
			history.add(new Document("from", current instanceof String text ? text : null).append("to", OPEN)
				.append("changedAt", Date.from(now))
				.append("changedBy", new Document("type", "SYSTEM").append("id", SYSTEM_AUTHOR)));
			after.put("status", OPEN);
		}
		after.put("statusHistory", history);
	}

	private static void putIfPresent(Document document, String field, Object value) {
		if (value != null) document.put(field, value);
	}

	/** True when the finding reopens a closed occurrence (for the log). */
	public static boolean reopens(Document before) {
		return before != null && CLOSED.equals(before.get("status"));
	}

}
