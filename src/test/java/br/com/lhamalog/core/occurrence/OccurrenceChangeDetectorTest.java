package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository.JournalEntry;
import br.com.lhamalog.core.occurrence.application.OccurrenceChangeDetector;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent.Change;

/** Spec 015, research R3: which writes of a delivery become notices. */
class OccurrenceChangeDetectorTest {

	private static final Instant AT = Instant.parse("2026-11-25T18:00:00Z");

	private static Document occurrence(String status, boolean archived) {
		Document document = new Document("_id", "o-1").append("type", "WARNING")
			.append("gravidade", "WARNING")
			.append("status", status);
		if (archived) document.append("archivedAt", Date.from(AT));
		return document;
	}

	private static List<OccurrenceEvent> detect(Document before, Document after) {
		return OccurrenceChangeDetector.detect("t-1", "p-1",
				List.of(new JournalEntry("o-1", before == null ? null : "r1", "r2", before, after)), AT);
	}

	@Test
	void createdOccurrenceIsANotice() {
		List<OccurrenceEvent> events = detect(null, occurrence("ABERTA", false));
		assertThat(events).containsExactly(new OccurrenceEvent("t-1", "p-1", "o-1", "WARNING", "WARNING", Change.CREATED, AT));
	}

	@Test
	void reopenedAndUnarchivedOccurrencesAreNotices() {
		assertThat(detect(occurrence("FECHADA", false), occurrence("ABERTA", false)))
			.extracting(OccurrenceEvent::change).containsExactly(Change.REOPENED);
		assertThat(detect(occurrence("EM_ANALISE", true), occurrence("ABERTA", false)))
			.extracting(OccurrenceEvent::change).containsExactly(Change.REOPENED);
		assertThat(detect(occurrence("ABERTA", true), occurrence("ABERTA", false)))
			.extracting(OccurrenceEvent::change).containsExactly(Change.REOPENED);
		assertThat(detect(occurrence("DESCONHECIDO", false), occurrence("ABERTA", false)))
			.extracting(OccurrenceEvent::change).containsExactly(Change.REOPENED);
	}

	@Test
	void countGoingUpOnAnActiveOccurrenceIsNotANotice() {
		assertThat(detect(occurrence("ABERTA", false), occurrence("ABERTA", false))).isEmpty();
		assertThat(detect(occurrence("EM_ANALISE", false), occurrence("EM_ANALISE", false))).isEmpty();
	}

	@Test
	void becameRecurringIsRecurrentOnlyWhenItIsTheStrongestChange() {
		Document open = occurrence("ABERTA", false);
		Document recurrent = occurrence("ABERTA", false).append("recurring", true);
		assertThat(detect(open, recurrent)).singleElement().satisfies(event -> {
			assertThat(event.change()).isEqualTo(Change.RECURRENT);
			assertThat(event.occurrenceType()).isEqualTo("WARNING");
			assertThat(event.gravidade()).isEqualTo("WARNING");
		});
		assertThat(detect(open.append("recurring", null), recurrent)).extracting(OccurrenceEvent::change)
			.containsExactly(Change.RECURRENT);
		assertThat(detect(occurrence("ABERTA", false).append("recurring", false), recurrent))
			.extracting(OccurrenceEvent::change).containsExactly(Change.RECURRENT);

		Document created = occurrence("ABERTA", false).append("recurring", true);
		assertThat(detect(null, created)).extracting(OccurrenceEvent::change).containsExactly(Change.CREATED);

		Document reopened = occurrence("ABERTA", false).append("recurring", true);
		assertThat(detect(occurrence("FECHADA", false), reopened)).extracting(OccurrenceEvent::change)
			.containsExactly(Change.REOPENED);
	}

}
