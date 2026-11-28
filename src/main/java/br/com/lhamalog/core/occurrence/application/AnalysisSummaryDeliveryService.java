package br.com.lhamalog.core.occurrence.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.DeliveryKey;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.Finding;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;
import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository.JournalEntry;
import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository.Receipt;

/**
 * Writes the findings of an analysis summary all or nothing, without a MongoDB transaction (spec 013, research R2):
 * reserve the key with a PENDING receipt, check every consulted revision, journal before and after, write each
 * occurrence conditionally on its revision, then confirm. A race or a failure while writing undoes what this attempt
 * wrote; a crashed attempt is undone by the next delivery of the same key once its lease is over.
 */
@Service
public class AnalysisSummaryDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisSummaryDeliveryService.class);

	private final OccurrenceWriteRepository occurrences;

	private final DeliveryReceiptRepository receipts;

	private final OccurrenceModuleProperties.Delivery settings;

	private final Clock clock;

	private final OccurrenceEventPublisher events;

	public AnalysisSummaryDeliveryService(OccurrenceWriteRepository occurrences, DeliveryReceiptRepository receipts,
			OccurrenceModuleProperties properties, Clock clock, OccurrenceEventPublisher events) {
		this.occurrences = occurrences;
		this.receipts = receipts;
		this.settings = properties.delivery();
		this.clock = clock;
		this.events = events;
	}

	public Result deliver(AnalysisSummary summary) {
		DeliveryKey key = summary.key();
		Optional<Receipt> existing = receipts.find(key);
		if (existing.isPresent()) {
			Receipt receipt = existing.get();
			if (receipt.confirmed()) return repeated(key, receipt);
			if (receipt.leaseUntil() != null && receipt.leaseUntil().isAfter(clock.instant())) throw inProgress(key);
			recover(key, receipt);
		}
		String attemptId = UUID.randomUUID().toString();
		String receiptId = UUID.randomUUID().toString();
		Instant now = clock.instant();
		if (!receipts.reserve(key, attemptId, receiptId, now, now.plus(settings.lease()))) {
			Receipt winner = receipts.find(key).orElseThrow(() -> inProgress(key));
			if (winner.confirmed()) return repeated(key, winner);
			throw inProgress(key);
		}
		List<JournalEntry> applied = new ArrayList<>();
		try {
			List<JournalEntry> journal = plan(summary, attemptId, now);
			if (!receipts.saveJournal(attemptId, journal)) throw lostReservation(key);
			for (JournalEntry entry : journal) {
				boolean written = entry.before() == null ? occurrences.insertNew(entry.after())
						: occurrences.replaceIfRevision(entry.occurrenceId(), entry.expectedRevision(), entry.after());
				if (!written) {
					undo(key, applied);
					receipts.release(attemptId);
					log.warn("event=occurrence.delivery.rolled_back | Occurrence changed while the delivery was written; undone | {} findings={}",
							describe(key), journal.size());
					throw stale(key);
				}
				applied.add(entry);
			}
			Instant confirmedAt = clock.instant();
			if (!receipts.confirm(attemptId, confirmedAt, confirmedAt.plus(settings.receiptRetention()), journal.size())) {
				throw lostReservation(key);
			}
			log.info("event=occurrence.delivery.accepted | Analysis summary written | {} findings={}", describe(key),
					journal.size());
			notifyScreens(key, journal, confirmedAt);
			return new Result(receiptId, false);
		}
		catch (InternalApiException refused) {
			throw refused;
		}
		catch (RuntimeException failure) {
			// Best effort: if the undo fails too, the receipt stays PENDING with its journal and the next delivery of
			// the key undoes it after the lease.
			try {
				undo(key, applied);
				receipts.release(attemptId);
			}
			catch (RuntimeException undoFailure) {
				log.error("event=occurrence.delivery.undo_failed | Delivery failed and could not be undone; left for recovery | {}",
						describe(key));
			}
			throw failure;
		}
	}

	/**
	 * Spec 015 and spec 016: the only publish of a delivery, after the receipt is confirmed. {@code CREATED},
	 * {@code REOPENED} and {@code RECURRENT} share this path. A failure here never fails the delivery.
	 */
	private void notifyScreens(DeliveryKey key, List<JournalEntry> journal, Instant at) {
		try {
			List<OccurrenceEvent> news = OccurrenceChangeDetector.detect(key.tenantId(), key.productId(), journal, at);
			if (!news.isEmpty()) events.publish(news);
		}
		catch (RuntimeException failure) {
			log.warn("event=occurrence.stream.publish_failed | Notices of an accepted delivery were not published | {} error={}",
					describe(key), failure.getClass().getSimpleName());
		}
	}

	/** Checks the consulted revisions and computes the journal; nothing is written. */
	private List<JournalEntry> plan(AnalysisSummary summary, String attemptId, Instant now) {
		DeliveryKey key = summary.key();
		List<OccurrenceKey> keys = summary.findings().stream().map(Finding::key).toList();
		Map<OccurrenceKey, Document> current;
		try {
			current = occurrences.findByKeys(key.tenantId(), key.productId(), keys);
		}
		catch (InternalApiException invalid) {
			receipts.release(attemptId);
			throw invalid;
		}
		for (Finding finding : summary.findings()) {
			Document stored = current.get(finding.key());
			String storedRevision = stored == null ? null : OccurrenceWriteRepository.revisionOf(stored);
			if (!Objects.equals(storedRevision, finding.consultedRevision())) {
				receipts.release(attemptId);
				log.warn("event=occurrence.delivery.stale_revision | Consulted revision is no longer current; nothing written | {} findings={}",
						describe(key), summary.findings().size());
				throw stale(key);
			}
		}
		List<JournalEntry> journal = new ArrayList<>();
		for (Finding finding : summary.findings()) {
			Document before = current.get(finding.key());
			String newRevision = UUID.randomUUID().toString();
			Document after = OccurrenceMerge.merge(before, finding, key.tenantId(), key.productId(),
					summary.rulesVersion(), now, newRevision, settings.maxSamples());
			if (before != null && before.get("count") instanceof Number previous && finding.count() < previous.longValue()) {
				log.warn("event=occurrence.delivery.count_decreased | Finding count is lower than the stored one; written as sent | {} occurrenceId={}",
						describe(key), before.get("_id"));
			}
			journal.add(new JournalEntry(after.getString("_id"),
					before == null ? null : OccurrenceWriteRepository.revisionOf(before), newRevision, before, after));
		}
		return journal;
	}

	/** Undoes a PENDING attempt whose lease is over (crash), then frees the key. */
	private void recover(DeliveryKey key, Receipt receipt) {
		undo(key, receipt.journal());
		receipts.release(receipt.attemptId());
		log.warn("event=occurrence.delivery.recovered | Interrupted delivery undone before a new attempt | {} findings={}",
				describe(key), receipt.journal().size());
	}

	/** Reverts, newest first, what an attempt wrote; skips what was changed after it. */
	private void undo(DeliveryKey key, List<JournalEntry> written) {
		int skipped = 0;
		for (int i = written.size() - 1; i >= 0; i--) {
			JournalEntry entry = written.get(i);
			boolean reverted = entry.before() == null ? occurrences.deleteIfRevision(entry.occurrenceId(), entry.newRevision())
					: occurrences.restoreIfRevision(entry.occurrenceId(), entry.newRevision(), entry.before());
			if (!reverted) skipped++;
		}
		if (skipped > 0) {
			log.warn("event=occurrence.delivery.undo_skipped | Occurrences changed after the attempt were left as they are | {} skipped={}",
					describe(key), skipped);
		}
	}

	private Result repeated(DeliveryKey key, Receipt receipt) {
		log.info("event=occurrence.delivery.repeated | Delivery key already accepted; original receipt returned | {}",
				describe(key));
		return new Result(receipt.receiptId(), true);
	}

	private InternalApiException inProgress(DeliveryKey key) {
		log.warn("event=occurrence.delivery.in_progress | Another delivery of the same key is running | {}", describe(key));
		return new InternalApiException(HttpStatus.CONFLICT, InternalApiException.DELIVERY_IN_PROGRESS);
	}

	private static InternalApiException stale(DeliveryKey key) {
		return new InternalApiException(HttpStatus.CONFLICT, InternalApiException.STALE_REVISION);
	}

	private InternalApiException lostReservation(DeliveryKey key) {
		log.error("event=occurrence.delivery.reservation_lost | Delivery reservation expired before confirmation | {}",
				describe(key));
		return new InternalApiException(HttpStatus.INTERNAL_SERVER_ERROR, InternalApiException.INTERNAL_ERROR);
	}

	private static String describe(DeliveryKey key) {
		return "tenantId=" + key.tenantId() + " productId=" + key.productId() + " batchId=" + key.batchId()
				+ " execId=" + (key.execId() == null ? "none" : key.execId());
	}

	/** Outcome of an accepted delivery: {@code repeated} for a key that was already confirmed. */
	public record Result(String receiptId, boolean repeated) {}

}
