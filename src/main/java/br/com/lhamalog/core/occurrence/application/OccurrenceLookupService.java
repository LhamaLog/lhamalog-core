package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;

/**
 * State of the occurrence of one finding, for the Finder to compute its totals (spec 013, US1). Read only.
 */
@Service
public class OccurrenceLookupService {

	private static final Logger log = LoggerFactory.getLogger(OccurrenceLookupService.class);

	private static final Set<String> STATUSES = Set.of("ABERTA", "EM_ANALISE", "FECHADA");

	private final OccurrenceWriteRepository occurrences;

	public OccurrenceLookupService(OccurrenceWriteRepository occurrences) {
		this.occurrences = occurrences;
	}

	/** Empty when no occurrence has the key. */
	public Optional<State> lookup(String tenantId, String productId, OccurrenceKey key) {
		Document stored = occurrences.findByKeys(tenantId, productId, List.of(key)).get(key);
		if (stored == null) return Optional.empty();
		if (!(stored.get("count") instanceof Number count) || count.longValue() < 1
				|| !(stored.get("firstSeenAt") instanceof Date first) || !(stored.get("lastSeenAt") instanceof Date last)
				|| !(stored.get("status") instanceof String status) || !STATUSES.contains(status)) {
			// Written outside the Module: cannot be answered in the contract format. No content in the log.
			log.error("event=occurrence.lookup.invalid | Stored occurrence lacks count, dates or status | tenantId={} productId={} occurrenceId={}",
					tenantId, productId, stored.get("_id"));
			throw new InternalApiException(HttpStatus.INTERNAL_SERVER_ERROR, InternalApiException.INVALID_OCCURRENCE);
		}
		return Optional.of(new State(count.longValue(), first.toInstant(), last.toInstant(), status,
				OccurrenceWriteRepository.revisionOf(stored)));
	}

	/** {@code EstadoOcorrencia} of the contract. */
	public record State(long count, Instant firstSeenAt, Instant lastSeenAt, String status, String revision) {}

}
