package br.com.lhamalog.core.occurrence.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.occurrence.api.OccurrenceResponses;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.CompanySession;

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

	public OccurrenceService(OccurrenceQueryRepository occurrences, ProductService products) {
		this.occurrences = occurrences;
		this.products = products;
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
				occurrence.firstSeenAt(), occurrence.lastSeenAt());
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
				occurrence.sensitiveData(), samples(occurrence.samples()), attributes(occurrence), history);
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
