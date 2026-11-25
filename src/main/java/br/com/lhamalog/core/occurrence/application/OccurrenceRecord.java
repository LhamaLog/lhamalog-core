package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A valid occurrence read from the collection written by the Occurrence Module. Optional fields stay {@code null};
 * lists and maps are never {@code null}.
 */
public record OccurrenceRecord(String id, String productId, OccurrenceType type, String signature, String title,
		String summary, OccurrenceStatus status, boolean recurring, long count, Instant firstSeenAt, Instant lastSeenAt,
		List<String> samples, String hostName, String traceId, String correlationId, String moduleId,
		Integer lineNumber, Map<String, String> extractedFields, Boolean sensitiveData,
		List<StatusChange> statusHistory) {

	public OccurrenceRecord {
		samples = samples == null ? List.of() : List.copyOf(samples);
		extractedFields = extractedFields == null ? Map.of() : Map.copyOf(extractedFields);
		statusHistory = statusHistory == null ? List.of() : List.copyOf(statusHistory);
	}

	/** One status change; values are kept as stored so a history entry never hides the occurrence. */
	public record StatusChange(String from, String to, Instant changedAt, String changedByType, String changedById) {}

	/** Title shown to the user: the stored one, or the message from the signature (research R13). */
	public String displayTitle() {
		return title != null && !title.isBlank() ? title : OccurrenceSignature.parse(signature).fallbackTitle();
	}
}
