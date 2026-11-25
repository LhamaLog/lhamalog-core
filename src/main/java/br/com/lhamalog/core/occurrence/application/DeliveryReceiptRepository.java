package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.DeliveryKey;

/**
 * Delivery receipts ({@code ReciboResumoAnalise}, spec 013 data-model): one per delivery key. A PENDING receipt holds
 * the key while a delivery runs and keeps the journal used to undo it; every write on it is conditional on the
 * {@code attemptId} that reserved it.
 */
public interface DeliveryReceiptRepository {

	Optional<Receipt> find(DeliveryKey key);

	/** Inserts a PENDING receipt; false when the key already has one. */
	boolean reserve(DeliveryKey key, String attemptId, String receiptId, Instant now, Instant leaseUntil);

	/** Stores the journal of the attempt; false when the reservation is lost. */
	boolean saveJournal(String attemptId, List<JournalEntry> journal);

	/** Marks the attempt CONFIRMED and drops the journal; false when the reservation is lost. */
	boolean confirm(String attemptId, Instant confirmedAt, Instant expiresAt, int findingCount);

	/** Deletes the PENDING receipt of the attempt (refused or undone delivery). */
	void release(String attemptId);

	enum State {
		PENDING, CONFIRMED
	}

	record Receipt(String receiptId, State state, String attemptId, Instant leaseUntil, List<JournalEntry> journal) {

		public Receipt {
			journal = journal == null ? List.of() : List.copyOf(journal);
		}

		public boolean confirmed() {
			return state == State.CONFIRMED;
		}

	}

	/**
	 * What one finding changed: {@code before} null means the delivery created the occurrence.
	 */
	record JournalEntry(String occurrenceId, String expectedRevision, String newRevision, Document before,
			Document after) {}

}
