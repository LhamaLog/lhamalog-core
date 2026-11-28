package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository.JournalEntry;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent.Change;

/**
 * Which writes of an accepted delivery are news for the screen (spec 015, research R3; spec 016): one notice per
 * occurrence, the strongest change of that delivery. Created beats reopened, which beats became-recurring
 * ({@link Change#RECURRENT}). A count going up on an active open occurrence is not a notice. Absent or null
 * {@code recurring} counts as false. Pure: works on the journal of the delivery.
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
				String type = entry.after().getString("type");
				events.add(new OccurrenceEvent(tenantId, productId, entry.occurrenceId(), type, severity(entry.after(), type),
						change, at));
			}
		}
		return events;
	}

	static Change change(Document before, Document after) {
		if (before == null) return Change.CREATED;
		boolean openNow = OPEN.equals(after.get("status"));
		boolean wasHidden = !OPEN.equals(before.get("status")) || before.get("archivedAt") != null;
		if (openNow && wasHidden) return Change.REOPENED;
		if (!recurring(before) && recurring(after)) return Change.RECURRENT;
		return null;
	}

	/** The class copied onto the occurrence. A document written before the field existed still uses {@code type}. */
	private static String severity(Document after, String type) {
		String gravidade = after.getString("gravidade");
		return gravidade == null || gravidade.isBlank() ? type : gravidade;
	}

	private static boolean recurring(Document document) {
		return document != null && Boolean.TRUE.equals(document.get("recurring"));
	}

}
