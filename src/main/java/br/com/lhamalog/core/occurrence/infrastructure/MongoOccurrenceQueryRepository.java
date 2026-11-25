package br.com.lhamalog.core.occurrence.infrastructure;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.occurrence.application.OccurrenceFilter;
import br.com.lhamalog.core.occurrence.application.OccurrenceQueryRepository;
import br.com.lhamalog.core.occurrence.application.OccurrenceRecord;
import br.com.lhamalog.core.occurrence.application.OccurrenceStatus;
import br.com.lhamalog.core.occurrence.application.OccurrenceType;

/**
 * Queries on the {@code Ocorrencia} collection. The list only matches valid documents (known type and status, required
 * fields present), so an invalid document never counts in the total; the detail reads the document as it is and
 * reports why it is invalid (research R7).
 */
@Repository
class MongoOccurrenceQueryRepository implements OccurrenceQueryRepository {

	private final MongoTemplate mongo;

	MongoOccurrenceQueryRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Page page(String tenantId, Collection<String> productIds, OccurrenceFilter filter) {
		Criteria criteria = criteria(tenantId, productIds, filter);
		long total = mongo.count(Query.query(criteria), OccurrenceDocument.class);
		long skip = (long) (filter.page() - 1) * filter.size();
		if (total <= skip) return new Page(List.of(), total);
		Query query = Query.query(criteria)
			.with(Sort.by(Sort.Order.desc(OccurrenceDocument.LAST_SEEN_AT), Sort.Order.desc("_id")))
			.skip(skip)
			.limit(filter.size());
		List<OccurrenceRecord> items = mongo.find(query, OccurrenceDocument.class).stream()
			.map(MongoOccurrenceQueryRepository::toRecord)
			.toList();
		return new Page(items, total);
	}

	@Override
	public Lookup findById(String tenantId, String id) {
		OccurrenceDocument document = mongo.findOne(
				Query.query(Criteria.where("_id").is(id).and(OccurrenceDocument.TENANT_ID).is(tenantId)),
				OccurrenceDocument.class);
		if (document == null) return new Lookup.Missing();
		String reason = invalidReason(document);
		return reason == null ? new Lookup.Found(toRecord(document)) : new Lookup.Invalid(reason);
	}

	private static Criteria criteria(String tenantId, Collection<String> productIds, OccurrenceFilter filter) {
		Criteria criteria = Criteria.where(OccurrenceDocument.TENANT_ID).is(tenantId)
			.and(OccurrenceDocument.PRODUCT_ID).in(productIds)
			.and(OccurrenceDocument.SIGNATURE).ne(null)
			.and(OccurrenceDocument.COUNT).ne(null)
			.and(OccurrenceDocument.TYPE).in(names(filter.types().isEmpty()
					? Arrays.asList(OccurrenceType.values()) : filter.types()))
			.and(OccurrenceDocument.STATUS).in(names(filter.statuses().isEmpty()
					? Arrays.asList(OccurrenceStatus.values()) : filter.statuses()));
		// "Happened in the period": first seen up to the end and last seen from the start (clarified 19/11).
		criteria = filter.to() == null ? criteria.and(OccurrenceDocument.FIRST_SEEN_AT).ne(null)
				: criteria.and(OccurrenceDocument.FIRST_SEEN_AT).lte(filter.to());
		criteria = filter.from() == null ? criteria.and(OccurrenceDocument.LAST_SEEN_AT).ne(null)
				: criteria.and(OccurrenceDocument.LAST_SEEN_AT).gte(filter.from());
		if (filter.text() != null) {
			Pattern pattern = Pattern.compile(Pattern.quote(filter.text()),
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			criteria = criteria.orOperator(Criteria.where(OccurrenceDocument.TITLE).regex(pattern),
					Criteria.where(OccurrenceDocument.SUMMARY).regex(pattern),
					Criteria.where(OccurrenceDocument.SIGNATURE).regex(pattern),
					Criteria.where("_id").is(filter.text()));
		}
		return criteria;
	}

	private static List<String> names(Collection<? extends Enum<?>> values) {
		return values.stream().map(Enum::name).toList();
	}

	/** {@code null} when the document can be shown; otherwise a short reason for the log (never its content). */
	static String invalidReason(OccurrenceDocument document) {
		if (document.productId() == null) return "missing_product";
		if (document.signature() == null) return "missing_signature";
		if (OccurrenceType.parse(document.type()).isEmpty()) return "unknown_type";
		if (OccurrenceStatus.parse(document.status()).isEmpty()) return "unknown_status";
		if (document.count() == null) return "missing_count";
		if (document.firstSeenAt() == null || document.lastSeenAt() == null) return "missing_dates";
		return null;
	}

	private static OccurrenceRecord toRecord(OccurrenceDocument document) {
		List<OccurrenceRecord.StatusChange> history = document.statusHistory() == null ? List.of()
				: document.statusHistory().stream()
					.map(change -> new OccurrenceRecord.StatusChange(change.from(), change.to(), change.changedAt(),
							change.changedBy() == null ? null : change.changedBy().type(),
							change.changedBy() == null ? null : change.changedBy().id()))
					.toList();
		return new OccurrenceRecord(document.id(), document.productId(),
				OccurrenceType.parse(document.type()).orElseThrow(), document.signature(), document.title(),
				document.summary(), OccurrenceStatus.parse(document.status()).orElseThrow(),
				Boolean.TRUE.equals(document.recurring()), document.count(), document.firstSeenAt(),
				document.lastSeenAt(), document.samples(), document.hostName(), document.traceId(),
				document.correlationId(), document.moduleId(), document.lineNumber(), document.extractedFields(),
				document.sensitiveData(), history);
	}
}
