package br.com.lhamalog.core.occurrence.application;

import java.util.Collection;
import java.util.List;

/** Read-only access to the occurrences written by the Occurrence Module (collection {@code Ocorrencia}). */
public interface OccurrenceQueryRepository {

	/**
	 * Valid occurrences of the tenant in the given products, filtered and paged, newest {@code lastSeenAt} first.
	 * Invalid documents are excluded from items and total.
	 */
	Page page(String tenantId, Collection<String> productIds, OccurrenceFilter filter);

	/** One occurrence of the tenant by id, telling apart a missing document from an invalid one. */
	Lookup findById(String tenantId, String id);

	record Page(List<OccurrenceRecord> items, long total) {
		public Page {
			items = List.copyOf(items);
		}
	}

	sealed interface Lookup {
		record Found(OccurrenceRecord occurrence) implements Lookup {}

		record Invalid(String reason) implements Lookup {}

		record Missing() implements Lookup {}
	}
}
