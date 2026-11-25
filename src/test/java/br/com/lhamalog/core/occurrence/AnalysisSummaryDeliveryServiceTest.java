package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.DeliveryKey;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.Finding;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;
import br.com.lhamalog.core.occurrence.application.AnalysisSummaryDeliveryService;
import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository;
import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;
import br.com.lhamalog.core.occurrence.application.OccurrenceWriteRepository;

/**
 * Spec 013, US3 and research R2: all or nothing without a transaction. In-memory repositories let the test break a
 * delivery in the middle, race it and crash it, which the integration tests cannot do on a real database.
 */
class AnalysisSummaryDeliveryServiceTest {

	private static final DeliveryKey KEY = new DeliveryKey("tenant-1", "product-1", "batch-1", null);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"));

	private final MemoryOccurrences occurrences = new MemoryOccurrences();

	private final MemoryReceipts receipts = new MemoryReceipts();

	private final List<OccurrenceEvent> published = new ArrayList<>();

	private final AnalysisSummaryDeliveryService service = new AnalysisSummaryDeliveryService(occurrences, receipts,
			new OccurrenceModuleProperties(null, null), clock, published::addAll);

	private static Finding finding(String signature, long count, String consultedRevision) {
		return new Finding(new OccurrenceKey(signature, "ERROR"), "host-1", count, Instant.parse("2026-11-20T10:00:00Z"),
				Instant.parse("2026-11-20T11:00:00Z"), false, List.of("linha"), consultedRevision, null, null, null, null,
				null, null, null, null);
	}

	private static AnalysisSummary summary(Finding... findings) {
		return new AnalysisSummary(KEY, "regras-v1", List.of(findings));
	}

	private String store(String signature, long count) {
		String id = "occ-" + signature;
		occurrences.data.put(id, new Document("_id", id).append("tenantId", "tenant-1")
			.append("productId", "product-1")
			.append("signature", signature)
			.append("type", "ERROR")
			.append("status", "ABERTA")
			.append("count", count)
			.append("revision", "rev-" + signature));
		return id;
	}

	private Map<String, Document> snapshot() {
		Map<String, Document> copy = new HashMap<>();
		occurrences.data.forEach((id, document) -> copy.put(id, new Document(document)));
		return copy;
	}

	@Test
	void writesEveryFindingAndConfirmsTheReceipt() {
		store("a", 10);
		AnalysisSummaryDeliveryService.Result result = service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 2, null)));
		assertThat(result.repeated()).isFalse();
		assertThat(occurrences.data).hasSize(2);
		assertThat(occurrences.data.get("occ-a").get("count")).isEqualTo(13L);
		assertThat(receipts.byKey.get(KEY).state()).isEqualTo(DeliveryReceiptRepository.State.CONFIRMED);
		assertThat(receipts.byKey.get(KEY).journal()).isEmpty();
		assertThat(receipts.expiresAt).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
	}

	@Test
	void repeatedKeyReturnsTheSameReceiptWithoutCheckingOrWriting() {
		store("a", 10);
		String receiptId = service.deliver(summary(finding("a", 13, "rev-a"))).receiptId();
		Map<String, Document> before = snapshot();
		AnalysisSummaryDeliveryService.Result again = service.deliver(summary(finding("a", 99, "revisao-velha")));
		assertThat(again.repeated()).isTrue();
		assertThat(again.receiptId()).isEqualTo(receiptId);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void staleRevisionRefusesEverythingBeforeWriting() {
		store("a", 10);
		store("b", 5);
		Map<String, Document> before = snapshot();
		assertCode(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 7, "outra"))),
				InternalApiException.STALE_REVISION);
		assertCode(() -> service.deliver(summary(finding("a", 13, null))), InternalApiException.STALE_REVISION);
		assertCode(() -> service.deliver(summary(finding("novo", 1, "rev-x"))), InternalApiException.STALE_REVISION);
		assertThat(snapshot()).isEqualTo(before);
		assertThat(receipts.byKey).isEmpty();
		assertThat(occurrences.writes).isZero();
	}

	@Test
	void raceWhileWritingUndoesWhatThisAttemptWrote() {
		store("a", 10);
		store("b", 5);
		Map<String, Document> before = snapshot();
		// Another delivery changes "b" after the check and before this attempt writes it.
		occurrences.beforeWrite = id -> {
			if (id.equals("occ-b")) occurrences.data.get("occ-b").put("revision", "rev-de-outro-lote");
		};
		assertCode(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 7, "rev-b"), finding("c", 1, null))),
				InternalApiException.STALE_REVISION);
		before.get("occ-b").put("revision", "rev-de-outro-lote");
		assertThat(snapshot()).isEqualTo(before);
		assertThat(receipts.byKey).isEmpty();
	}

	@Test
	void failureWhileWritingUndoesAndFreesTheKey() {
		store("a", 10);
		Map<String, Document> before = snapshot();
		occurrences.failOnWrite = 2;
		assertThatThrownBy(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null), finding("c", 1, null))))
			.isInstanceOf(IllegalStateException.class);
		assertThat(snapshot()).isEqualTo(before);
		assertThat(receipts.byKey).isEmpty();
		occurrences.failOnWrite = 0;
		assertThat(service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null), finding("c", 1, null))).repeated())
			.isFalse();
		assertThat(occurrences.data).hasSize(3);
	}

	@Test
	void crashedAttemptIsUndoneByTheNextDeliveryAfterItsLease() {
		store("a", 10);
		Map<String, Document> before = snapshot();
		// The Core dies after writing the first finding: receipt PENDING with its journal, no undo.
		occurrences.crashOnWrite = 2;
		assertThatThrownBy(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null))))
			.isInstanceOf(Crash.class);
		assertThat(receipts.byKey.get(KEY).state()).isEqualTo(DeliveryReceiptRepository.State.PENDING);
		assertThat(occurrences.data.get("occ-a").get("count")).isEqualTo(13L);
		occurrences.crashOnWrite = 0;
		occurrences.dead = false;

		assertCode(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null))),
				InternalApiException.DELIVERY_IN_PROGRESS);
		assertThat(occurrences.data.get("occ-a").get("count")).isEqualTo(13L);

		clock.advance(Duration.ofSeconds(31));
		AnalysisSummaryDeliveryService.Result retried = service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null)));
		assertThat(retried.repeated()).isFalse();
		assertThat(occurrences.data.get("occ-a").get("count")).isEqualTo(13L);
		assertThat(occurrences.data).hasSize(2);
		assertThat(receipts.byKey.get(KEY).state()).isEqualTo(DeliveryReceiptRepository.State.CONFIRMED);
		assertThat(before).containsKey("occ-a");
	}

	@Test
	void undoLeavesAloneWhatWasChangedAfterTheAttempt() {
		store("a", 10);
		occurrences.crashOnWrite = 2;
		assertThatThrownBy(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null))))
			.isInstanceOf(Crash.class);
		occurrences.crashOnWrite = 0;
		occurrences.dead = false;
		// Someone else wrote "a" after the crashed attempt.
		occurrences.data.get("occ-a").put("revision", "rev-posterior");
		occurrences.data.get("occ-a").put("count", 40L);
		clock.advance(Duration.ofSeconds(31));
		assertCode(() -> service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 1, null))),
				InternalApiException.STALE_REVISION);
		assertThat(occurrences.data.get("occ-a").get("count")).isEqualTo(40L);
	}

	@Test
	void concurrentDeliveryOfTheSameKeyIsInProgress() {
		receipts.reserve(KEY, "other-attempt", "receipt-x", clock.instant(), clock.instant().plusSeconds(30));
		assertCode(() -> service.deliver(summary(finding("a", 1, null))), InternalApiException.DELIVERY_IN_PROGRESS);
	}

	@Test
	void lostReservationBeforeConfirmationIsAnInternalError() {
		receipts.loseOnConfirm = true;
		assertCode(() -> service.deliver(summary(finding("a", 1, null))), InternalApiException.INTERNAL_ERROR);
	}

	// Spec 015: notices only after an accepted delivery

	@Test
	void acceptedDeliveryPublishesCreatedAndReopenedOnly() {
		store("a", 10);
		occurrences.data.get("occ-a").put("status", "FECHADA");
		store("b", 5);
		service.deliver(summary(finding("a", 13, "rev-a"), finding("b", 6, "rev-b"), finding("c", 1, null)));
		assertThat(published).extracting(OccurrenceEvent::occurrenceId, OccurrenceEvent::change)
			.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("occ-a", OccurrenceEvent.Change.REOPENED),
					org.assertj.core.groups.Tuple.tuple(occurrences.data.values().stream()
						.filter(d -> d.getString("signature").equals("c")).findFirst().orElseThrow().getString("_id"),
							OccurrenceEvent.Change.CREATED));
	}

	@Test
	void refusedRepeatedAndUndoneDeliveriesPublishNothing() {
		store("a", 10);
		assertCode(() -> service.deliver(summary(finding("a", 13, "outra"))), InternalApiException.STALE_REVISION);
		occurrences.failOnWrite = 2;
		assertThatThrownBy(() -> service.deliver(summary(finding("x", 1, null), finding("y", 1, null))))
			.isInstanceOf(IllegalStateException.class);
		occurrences.failOnWrite = 0;
		assertThat(published).isEmpty();
		service.deliver(summary(finding("z", 1, null)));
		published.clear();
		service.deliver(summary(finding("z", 1, null)));
		assertThat(published).isEmpty();
	}

	@Test
	void aFailingPublisherDoesNotFailTheDelivery() {
		AnalysisSummaryDeliveryService failing = new AnalysisSummaryDeliveryService(occurrences, receipts,
				new OccurrenceModuleProperties(null, null), clock, events -> {
					throw new IllegalStateException("redis fora");
				});
		assertThat(failing.deliver(summary(finding("n", 1, null))).repeated()).isFalse();
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(InternalApiException.class,
				refused -> assertThat(refused.code()).isEqualTo(code));
	}

	static final class Crash extends RuntimeException {}

	/** Occurrences in memory with the same conditional semantics as the Mongo repository. */
	static final class MemoryOccurrences implements OccurrenceWriteRepository {

		final Map<String, Document> data = new LinkedHashMap<>();

		Consumer<String> beforeWrite = id -> {};

		int failOnWrite;

		int crashOnWrite;

		/** After a crash the process is gone: the undo cannot run either. */
		boolean dead;

		int writes;

		@Override
		public Map<OccurrenceKey, Document> findByKeys(String tenantId, String productId, Collection<OccurrenceKey> keys) {
			Map<OccurrenceKey, Document> found = new HashMap<>();
			for (Document document : data.values()) {
				OccurrenceKey key = new OccurrenceKey(document.getString("signature"), document.getString("type"));
				if (keys.contains(key)) found.put(key, new Document(document));
			}
			return found;
		}

		private void count(String id) {
			writes++;
			if (crashOnWrite > 0 && writes >= crashOnWrite) {
				dead = true;
				throw new Crash();
			}
			if (failOnWrite > 0 && writes >= failOnWrite) throw new IllegalStateException("falha simulada");
			beforeWrite.accept(id);
		}

		@Override
		public boolean replaceIfRevision(String id, String expectedRevision, Document after) {
			count(id);
			Document current = data.get(id);
			if (current == null || !OccurrenceWriteRepository.revisionOf(current).equals(expectedRevision)) return false;
			data.put(id, new Document(after));
			return true;
		}

		@Override
		public boolean insertNew(Document after) {
			count(after.getString("_id"));
			boolean exists = data.values().stream().anyMatch(d -> d.getString("signature").equals(after.getString("signature"))
					&& d.getString("type").equals(after.getString("type")));
			if (exists) return false;
			data.put(after.getString("_id"), new Document(after));
			return true;
		}

		@Override
		public boolean restoreIfRevision(String id, String writtenRevision, Document before) {
			if (dead) throw new Crash();
			Document current = data.get(id);
			if (current == null || !writtenRevision.equals(current.get("revision"))) return false;
			data.put(id, new Document(before));
			return true;
		}

		@Override
		public boolean deleteIfRevision(String id, String writtenRevision) {
			if (dead) throw new Crash();
			Document current = data.get(id);
			if (current == null || !writtenRevision.equals(current.get("revision"))) return false;
			data.remove(id);
			return true;
		}

	}

	/** Receipts in memory, conditional on the attempt like the Mongo repository. */
	static final class MemoryReceipts implements DeliveryReceiptRepository {

		final Map<DeliveryKey, Receipt> byKey = new HashMap<>();

		Instant expiresAt;

		boolean loseOnConfirm;

		@Override
		public Optional<Receipt> find(DeliveryKey key) {
			return Optional.ofNullable(byKey.get(key));
		}

		@Override
		public boolean reserve(DeliveryKey key, String attemptId, String receiptId, Instant now, Instant leaseUntil) {
			if (byKey.containsKey(key)) return false;
			byKey.put(key, new Receipt(receiptId, State.PENDING, attemptId, leaseUntil, List.of()));
			return true;
		}

		private Optional<Map.Entry<DeliveryKey, Receipt>> pending(String attemptId) {
			return byKey.entrySet().stream()
				.filter(e -> e.getValue().attemptId().equals(attemptId) && e.getValue().state() == State.PENDING)
				.findFirst();
		}

		@Override
		public boolean saveJournal(String attemptId, List<JournalEntry> journal) {
			return pending(attemptId).map(e -> {
				Receipt r = e.getValue();
				byKey.put(e.getKey(), new Receipt(r.receiptId(), r.state(), r.attemptId(), r.leaseUntil(), new ArrayList<>(journal)));
				return true;
			}).orElse(false);
		}

		@Override
		public boolean confirm(String attemptId, Instant confirmedAt, Instant expiresAt, int findingCount) {
			if (loseOnConfirm) return false;
			this.expiresAt = expiresAt;
			return pending(attemptId).map(e -> {
				byKey.put(e.getKey(), new Receipt(e.getValue().receiptId(), State.CONFIRMED, attemptId, null, List.of()));
				return true;
			}).orElse(false);
		}

		@Override
		public void release(String attemptId) {
			pending(attemptId).ifPresent(e -> byKey.remove(e.getKey()));
		}

	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
