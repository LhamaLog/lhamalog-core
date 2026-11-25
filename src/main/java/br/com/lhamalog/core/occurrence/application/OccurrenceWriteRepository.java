package br.com.lhamalog.core.occurrence.application;

import java.util.Collection;
import java.util.Map;

import org.bson.Document;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;

/**
 * Raw access of the Occurrence Module to the {@code Ocorrencia} collection (spec 013). Every write is conditional on the
 * revision, so a concurrent change is detected instead of overwritten (research R2). Raw documents keep extracted field
 * names with dots ({@code http.route}).
 */
public interface OccurrenceWriteRepository {

	/** Revision value of a stored occurrence written without one (research R3). */
	String NO_REVISION = "sem-revisao";

	/**
	 * Occurrences of the keys inside a tenant and product, read at once.
	 *
	 * @throws InternalApiException 500 {@code OCORRENCIA_INVALIDA} when a key has more than one document
	 */
	Map<OccurrenceKey, Document> findByKeys(String tenantId, String productId, Collection<OccurrenceKey> keys);

	/** Replaces the occurrence only if its revision is still {@code expectedRevision}. */
	boolean replaceIfRevision(String id, String expectedRevision, Document after);

	/** Inserts a new occurrence; false when another one with the same key already exists. */
	boolean insertNew(Document after);

	/** Undo of a replace: puts {@code before} back only if the revision is still the one this delivery wrote. */
	boolean restoreIfRevision(String id, String writtenRevision, Document before);

	/** Undo of an insert: deletes only if the revision is still the one this delivery wrote. */
	boolean deleteIfRevision(String id, String writtenRevision);

	/** Revision of a stored occurrence, {@link #NO_REVISION} when it has none. */
	static String revisionOf(Document occurrence) {
		return occurrence.get("revision") instanceof String revision && !revision.isEmpty() ? revision : NO_REVISION;
	}

}
