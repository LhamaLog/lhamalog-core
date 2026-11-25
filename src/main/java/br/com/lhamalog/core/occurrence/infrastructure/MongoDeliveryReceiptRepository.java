package br.com.lhamalog.core.occurrence.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary.DeliveryKey;
import br.com.lhamalog.core.occurrence.application.DeliveryReceiptRepository;

/**
 * Receipts in {@code ReciboResumoAnalise} (spec 013 data-model). The unique key index makes the reservation atomic;
 * the TTL index removes confirmed receipts after the retention period. PENDING receipts have no {@code expiresAt} and
 * stay until the next delivery of their key resolves them.
 */
@Repository
public class MongoDeliveryReceiptRepository implements DeliveryReceiptRepository {

	static final String COLLECTION = "ReciboResumoAnalise";

	private final MongoTemplate mongo;

	public MongoDeliveryReceiptRepository(MongoTemplate mongo) {
		this.mongo = mongo;
		collection().createIndex(Indexes.ascending("tenantId", "productId", "batchId", "execId"),
				new IndexOptions().name("delivery_receipt_key").unique(true));
		collection().createIndex(Indexes.ascending("expiresAt"),
				new IndexOptions().name("delivery_receipt_expiry").expireAfter(0L, TimeUnit.SECONDS));
	}

	@Override
	public Optional<Receipt> find(DeliveryKey key) {
		Document document = collection().find(keyFilter(key)).first();
		return Optional.ofNullable(document).map(MongoDeliveryReceiptRepository::receipt);
	}

	@Override
	public boolean reserve(DeliveryKey key, String attemptId, String receiptId, Instant now, Instant leaseUntil) {
		Document receipt = new Document("_id", UUID.randomUUID().toString()).append("tenantId", key.tenantId())
			.append("productId", key.productId())
			.append("batchId", key.batchId())
			.append("execId", execId(key))
			.append("reciboId", receiptId)
			.append("state", State.PENDING.name())
			.append("attemptId", attemptId)
			.append("createdAt", Date.from(now))
			.append("leaseUntil", Date.from(leaseUntil));
		try {
			collection().insertOne(receipt);
			return true;
		}
		catch (MongoWriteException failure) {
			if (ErrorCategory.fromErrorCode(failure.getError().getCode()) == ErrorCategory.DUPLICATE_KEY) return false;
			throw failure;
		}
	}

	@Override
	public boolean saveJournal(String attemptId, List<JournalEntry> journal) {
		List<Document> entries = journal.stream()
			.map(entry -> new Document("occurrenceId", entry.occurrenceId())
				.append("expectedRevision", entry.expectedRevision())
				.append("newRevision", entry.newRevision())
				.append("before", entry.before())
				.append("after", entry.after()))
			.toList();
		return collection().updateOne(pending(attemptId),
				Updates.combine(Updates.set("journal", entries), Updates.set("findingCount", journal.size())))
			.getMatchedCount() == 1;
	}

	@Override
	public boolean confirm(String attemptId, Instant confirmedAt, Instant expiresAt, int findingCount) {
		return collection().updateOne(pending(attemptId),
				Updates.combine(Updates.set("state", State.CONFIRMED.name()), Updates.set("confirmedAt", Date.from(confirmedAt)),
						Updates.set("expiresAt", Date.from(expiresAt)), Updates.set("findingCount", findingCount),
						Updates.unset("journal"), Updates.unset("leaseUntil")))
			.getMatchedCount() == 1;
	}

	@Override
	public void release(String attemptId) {
		collection().deleteOne(pending(attemptId));
	}

	private static Receipt receipt(Document document) {
		List<JournalEntry> journal = new ArrayList<>();
		if (document.get("journal") instanceof List<?> entries) {
			for (Object item : entries) {
				if (item instanceof Document entry) {
					journal.add(new JournalEntry(entry.getString("occurrenceId"), entry.getString("expectedRevision"),
							entry.getString("newRevision"), entry.get("before", Document.class),
							entry.get("after", Document.class)));
				}
			}
		}
		Date leaseUntil = document.getDate("leaseUntil");
		return new Receipt(document.getString("reciboId"), State.valueOf(document.getString("state")),
				document.getString("attemptId"), leaseUntil == null ? null : leaseUntil.toInstant(), journal);
	}

	private static org.bson.conversions.Bson keyFilter(DeliveryKey key) {
		return Filters.and(Filters.eq("tenantId", key.tenantId()), Filters.eq("productId", key.productId()),
				Filters.eq("batchId", key.batchId()), Filters.eq("execId", execId(key)));
	}

	private static org.bson.conversions.Bson pending(String attemptId) {
		return Filters.and(Filters.eq("attemptId", attemptId), Filters.eq("state", State.PENDING.name()));
	}

	/** Absent execId is stored as an empty string, so the unique index covers both key forms. */
	private static String execId(DeliveryKey key) {
		return key.execId() == null ? "" : key.execId();
	}

	private MongoCollection<Document> collection() {
		return mongo.getCollection(COLLECTION);
	}

}
