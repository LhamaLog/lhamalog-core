package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository.JournalEntry;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent.Change;

/**
 * Which writes of an accepted delivery are news for the screen (spec 015, research R3): a created occurrence, and one
 * that became ABERTA coming from another status or from archived. A count going up on an active open occurrence is
 * not. Pure: works on the journal of the delivery.
 */
public final class OccurrenceChangeDetector {

	private static final String OPEN = "ABERTA";

	private OccurrenceChangeDetector() {
	}

	public static List<OccurrenceEvent> detect(String tenantId, String productId, List<JournalEntry> journal,
			Instant at) {
		List<OccurrenceEvent> events = new ArrayList<>();
		for (JournalEntry entry : journal) {
			Change change = change(entry.before(), entry.after());
			if (change != null) {
				events.add(new OccurrenceEvent(tenantId, productId, entry.occurrenceId(),
						entry.after().getString("type"), change, at));
			}
		}
		return events;
	}

	static Change change(Document before, Document after) {
		if (before == null) return Change.CREATED;
		boolean openNow = OPEN.equals(after.get("status"));
		boolean wasHidden = !OPEN.equals(before.get("status")) || before.get("archivedAt") != null;
		return openNow && wasHidden ? Change.REOPENED : null;
	}

}
