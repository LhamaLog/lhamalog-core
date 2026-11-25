package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A validated {@code ResumoAnalise} of the Finder (contract DN-009-2): the delivery key, the rules version of the
 * batch and its findings. Only what the Occurrence Module writes is kept; the counters are validated and dropped.
 */
public record AnalysisSummary(DeliveryKey key, String rulesVersion, List<Finding> findings) {

	public AnalysisSummary {
		findings = List.copyOf(findings);
	}

	/** Delivery key: {@code execId} is null when the summary has none. */
	public record DeliveryKey(String tenantId, String productId, String batchId, String execId) {}

	/** Occurrence key inside a tenant and product. */
	public record OccurrenceKey(String signature, String type) {}

	/**
	 * One {@code AchadoAnalise}. Nullable fields keep the previous value of the occurrence; {@code consultedRevision}
	 * null means the Finder saw no occurrence.
	 */
	public record Finding(OccurrenceKey key, String hostName, long count, Instant firstSeenAt, Instant lastSeenAt,
			boolean recurring, List<String> lines, String consultedRevision, String title, String summary,
			Integer lineNumber, Map<String, String> extractedFields, Boolean sensitiveData, String traceId,
			String correlationId, String moduleId) {

		public Finding {
			lines = List.copyOf(lines);
			extractedFields = extractedFields == null ? null : Map.copyOf(extractedFields);
		}

	}

}
