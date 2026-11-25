package br.com.lhamalog.core.occurrence.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.access.application.UserHash;
import br.com.lhamalog.core.occurrence.api.OccurrenceResponses;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.FieldValidationException;

/**
 * List and detail of occurrences (LLT-135). Visibility follows the product rules of feature 001: owners and admins see
 * every product of the company, archived ones included; other members only the products they have access to.
 */
@Service
public class OccurrenceService {

	static final int MAX_SAMPLES = 5;

	static final int MAX_LINE = 512;

	private static final Logger log = LoggerFactory.getLogger(OccurrenceService.class);

	private static final String PRODUCT_NOT_FOUND = "Produto não encontrado ou sem acesso.";

	private static final String OCCURRENCE_NOT_FOUND = "Ocorrência não encontrada ou sem acesso.";

	private final OccurrenceQueryRepository occurrences;

	private final ProductService products;

	private final OccurrenceCommandRepository commands;

	private final Clock clock;

	static final String CHANGED = "A ocorrência mudou desde que você abriu. Recarregue e tente de novo.";

	static final String CHANGED_CODE = "occurrence_changed";

	public OccurrenceService(OccurrenceQueryRepository occurrences, ProductService products,
			OccurrenceCommandRepository commands, Clock clock) {
		this.occurrences = occurrences;
		this.products = products;
		this.commands = commands;
		this.clock = clock;
	}

	public OccurrenceResponses.Page list(CompanySession session, OccurrenceFilter filter) {
		Map<String, String> visible = visibleProducts(session);
		Set<String> productIds = filter.productIds().isEmpty() ? visible.keySet() : filter.productIds();
		if (!visible.keySet().containsAll(productIds)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND);
		}
		if (productIds.isEmpty()) return new OccurrenceResponses.Page(List.of(), filter.page(), filter.size(), 0);
		OccurrenceQueryRepository.Page page = occurrences.page(session.tenantId(), productIds, filter);
		List<OccurrenceResponses.ListItem> items = page.items().stream()
			.map(occurrence -> listItem(occurrence, visible))
			.toList();
		return new OccurrenceResponses.Page(items, filter.page(), filter.size(), page.total());
	}

	/** Spec 014, US1: any of the three statuses, from any other; the same status changes nothing. */
	public OccurrenceResponses.Detail changeStatus(CompanySession session, String id, String status, String revision) {
		Map<String, String> errors = new LinkedHashMap<>();
		OccurrenceStatus target = OccurrenceStatus.parse(status == null ? null : status.trim()).orElse(null);
		if (target == null) errors.put("status", "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA.");
		requireRevision(revision, errors);
		if (!errors.isEmpty()) throw new FieldValidationException(errors);
		Visible current = visible(session, id);
		if (current.occurrence().status() == target) return detail(current.occurrence(), current.products());
		boolean changed = commands.changeStatus(session.tenantId(), id, revision.trim(), target.name(),
				current.occurrence().status().name(), clock.instant(), session.userId(), newRevision());
		return afterAction(session, id, changed, "event=occurrence.status.changed | Occurrence status changed by a user",
				"to=" + target.name());
	}

	/** Spec 014, US2: archiving keeps the status; archiving an archived occurrence changes nothing. */
	public OccurrenceResponses.Detail archive(CompanySession session, String id, String revision) {
		requireRevisionOnly(revision);
		Visible current = visible(session, id);
		if (current.occurrence().archived()) return detail(current.occurrence(), current.products());
		Instant now = clock.instant();
		boolean changed = commands.archive(session.tenantId(), id, revision.trim(), now, session.userId(), newRevision());
		return afterAction(session, id, changed, "event=occurrence.archived | Occurrence archived by a user", "");
	}

	public OccurrenceResponses.Detail unarchive(CompanySession session, String id, String revision) {
		requireRevisionOnly(revision);
		Visible current = visible(session, id);
		if (!current.occurrence().archived()) return detail(current.occurrence(), current.products());
		boolean changed = commands.unarchive(session.tenantId(), id, revision.trim(), newRevision());
		return afterAction(session, id, changed, "event=occurrence.unarchived | Occurrence unarchived by a user", "");
	}

	/** Re-reads after a conditional write: changed → new detail; not changed → 404 if gone, else 409 (research R2). */
	private OccurrenceResponses.Detail afterAction(CompanySession session, String id, boolean changed, String event,
			String extra) {
		Visible now = visible(session, id);
		if (!changed) {
			log.warn("event=occurrence.action.stale | Occurrence changed since the user opened it; nothing written | tenantId={} occurrenceId={} userHash={}",
					session.tenantId(), id, UserHash.of(session.userId()));
			throw new ApiStatusException(HttpStatus.CONFLICT, CHANGED, CHANGED_CODE);
		}
		log.info("{} | tenantId={} occurrenceId={} userHash={} {}", event, session.tenantId(), id,
				UserHash.of(session.userId()), extra);
		return detail(now.occurrence(), now.products());
	}

	/** The occurrence as the detail sees it; 404 for another company, a product without access or an invalid one. */
	private Visible visible(CompanySession session, String id) {
		if (!(occurrences.findById(session.tenantId(), id) instanceof OccurrenceQueryRepository.Lookup.Found found)) {
			throw notFound();
		}
		Map<String, String> visible = visibleProducts(session);
		if (!visible.containsKey(found.occurrence().productId())) throw notFound();
		return new Visible(found.occurrence(), visible);
	}

	private record Visible(OccurrenceRecord occurrence, Map<String, String> products) {}

	private static void requireRevision(String revision, Map<String, String> errors) {
		if (revision == null || revision.isBlank()) errors.put("revision", "Informe a revisão que você está vendo.");
	}

	private static void requireRevisionOnly(String revision) {
		Map<String, String> errors = new LinkedHashMap<>();
		requireRevision(revision, errors);
		if (!errors.isEmpty()) throw new FieldValidationException(errors);
	}

	private static String newRevision() {
		return UUID.randomUUID().toString();
	}

	public OccurrenceResponses.Detail detail(CompanySession session, String id) {
		OccurrenceQueryRepository.Lookup lookup = occurrences.findById(session.tenantId(), id);
		if (lookup instanceof OccurrenceQueryRepository.Lookup.Invalid invalid) {
			// Written by another service: hidden from the user and reported, never with its content (constitution VI).
			log.warn("event=occurrence.read.invalid | Stored occurrence is invalid; hidden from the user | tenantId={} occurrenceId={} reason={}",
					session.tenantId(), id, invalid.reason());
			throw notFound();
		}
		if (!(lookup instanceof OccurrenceQueryRepository.Lookup.Found found)) throw notFound();
		OccurrenceRecord occurrence = found.occurrence();
		Map<String, String> visible = visibleProducts(session);
		if (!visible.containsKey(occurrence.productId())) throw notFound();
		return detail(occurrence, visible);
	}

	/** Product id → name of every product the session can see, active and archived. */
	private Map<String, String> visibleProducts(CompanySession session) {
		Map<String, String> visible = new LinkedHashMap<>();
		for (Product product : products.list(session, false)) visible.put(product.id(), product.name());
		for (Product product : products.list(session, true)) visible.put(product.id(), product.name());
		return visible;
	}

	private static OccurrenceResponses.ListItem listItem(OccurrenceRecord occurrence, Map<String, String> products) {
		return new OccurrenceResponses.ListItem(occurrence.id(), occurrence.type().name(), occurrence.displayTitle(),
				product(occurrence, products), occurrence.count(), occurrence.status().name(), occurrence.recurring(),
				occurrence.firstSeenAt(), occurrence.lastSeenAt(), occurrence.archived());
	}

	private static OccurrenceResponses.Detail detail(OccurrenceRecord occurrence, Map<String, String> products) {
		List<OccurrenceResponses.StatusChange> history = occurrence.statusHistory().stream()
			.sorted(Comparator.comparing(OccurrenceRecord.StatusChange::changedAt,
					Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(change -> new OccurrenceResponses.StatusChange(change.from(), change.to(), change.changedAt(),
					new OccurrenceResponses.ChangedBy(change.changedByType(), change.changedById())))
			.toList();
		return new OccurrenceResponses.Detail(occurrence.id(), occurrence.type().name(), occurrence.displayTitle(),
				blankToNull(occurrence.summary()), product(occurrence, products), occurrence.count(),
				occurrence.status().name(), occurrence.recurring(), occurrence.firstSeenAt(), occurrence.lastSeenAt(),
				occurrence.sensitiveData(), samples(occurrence.samples()), attributes(occurrence), history,
				occurrence.revision(), occurrence.archived(), occurrence.archivedAt(),
				occurrence.archivedAt() == null ? null
						: new OccurrenceResponses.ChangedBy(occurrence.archivedByType(), occurrence.archivedById()));
	}

	private static OccurrenceResponses.ProductRef product(OccurrenceRecord occurrence, Map<String, String> products) {
		return new OccurrenceResponses.ProductRef(occurrence.productId(), products.get(occurrence.productId()));
	}

	/** The last stored samples are the newest ones: the five newest, newest first, each cut to 512 characters. */
	static List<String> samples(List<String> stored) {
		List<String> newestFirst = new ArrayList<>();
		for (int i = stored.size() - 1; i >= 0 && newestFirst.size() < MAX_SAMPLES; i--) {
			String line = stored.get(i);
			if (line != null) newestFirst.add(cut(line));
		}
		return newestFirst;
	}

	static String cut(String line) {
		if (line.length() <= MAX_LINE) return line;
		int end = Character.isHighSurrogate(line.charAt(MAX_LINE - 1)) ? MAX_LINE - 1 : MAX_LINE;
		return line.substring(0, end);
	}

	/** Technical attributes in alphabetical order; empty values are left out (data-model, "Atributos"). */
	static List<OccurrenceResponses.Attribute> attributes(OccurrenceRecord occurrence) {
		List<OccurrenceResponses.Attribute> attributes = new ArrayList<>();
		add(attributes, "host.name", occurrence.hostName());
		add(attributes, "service.name", OccurrenceSignature.parse(occurrence.signature()).service());
		add(attributes, "trace_id", occurrence.traceId());
		add(attributes, "correlation_id", occurrence.correlationId());
		add(attributes, "module_id", occurrence.moduleId());
		add(attributes, "line_number", occurrence.lineNumber() == null ? null : occurrence.lineNumber().toString());
		occurrence.extractedFields().forEach((name, value) -> add(attributes, name, value));
		attributes.sort(Comparator.comparing(OccurrenceResponses.Attribute::name)
			.thenComparing(OccurrenceResponses.Attribute::value));
		return attributes;
	}

	private static void add(List<OccurrenceResponses.Attribute> attributes, String name, String value) {
		if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
			attributes.add(new OccurrenceResponses.Attribute(name, value));
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, OCCURRENCE_NOT_FOUND);
	}
}
