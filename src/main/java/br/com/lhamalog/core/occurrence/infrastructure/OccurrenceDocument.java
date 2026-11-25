package br.com.lhamalog.core.occurrence.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Occurrence as written by the Occurrence Module from the Finder {@code AchadoAnalise} (contract DN-009-2, see
 * {@code specs/012-consultar-ocorrencias/data-model.md}). The Core only reads it here, except for the local sample
 * data. {@code type} and {@code status} stay strings so an unknown value can be read and reported instead of failing
 * the whole query.
 */
@Document("Ocorrencia")
@CompoundIndexes({
		@CompoundIndex(name = "occurrence_tenant_product_last_seen",
				def = "{'tenantId': 1, 'productId': 1, 'lastSeenAt': -1, '_id': -1}"),
		@CompoundIndex(name = "occurrence_unique_key",
				def = "{'tenantId': 1, 'productId': 1, 'signature': 1, 'type': 1}", unique = true)
})
public record OccurrenceDocument(@Id String id, String tenantId, String productId, String signature, String type,
		String title, String summary, String status, Boolean recurring, Long count, Instant firstSeenAt,
		Instant lastSeenAt, List<String> samples, String hostName, String traceId, String correlationId,
		String moduleId, Integer lineNumber, Map<String, String> extractedFields, Boolean sensitiveData,
		String rulesVersion, String revision, List<StatusChange> statusHistory) {

	static final String TENANT_ID = "tenantId";
	static final String PRODUCT_ID = "productId";
	static final String SIGNATURE = "signature";
	static final String TYPE = "type";
	static final String TITLE = "title";
	static final String SUMMARY = "summary";
	static final String STATUS = "status";
	static final String COUNT = "count";
	static final String FIRST_SEEN_AT = "firstSeenAt";
	static final String LAST_SEEN_AT = "lastSeenAt";

	public record StatusChange(String from, String to, Instant changedAt, ChangedBy changedBy) {}

	/** {@code type} is {@code USER} (id = Clerk user id) or {@code SYSTEM} (id = service name). */
	public record ChangedBy(String type, String id) {}
}
