package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.lhamalog.core.shared.FieldValidationException;

/**
 * Validated parameters of the occurrence list ({@code FiltroOcorrenciaEmpresarial}). Empty sets mean "no filter";
 * {@code from}, {@code to} and {@code text} are {@code null} when absent.
 */
public record OccurrenceFilter(int page, int size, Set<OccurrenceType> types, Set<OccurrenceStatus> statuses,
		Set<String> productIds, Instant from, Instant to, String text, boolean archived) {

	public static final int DEFAULT_SIZE = 20;

	static final Set<Integer> SIZES = Set.of(10, 20, 50);

	static final int MAX_TEXT = 200;

	public OccurrenceFilter {
		types = Set.copyOf(types);
		statuses = Set.copyOf(statuses);
		productIds = Set.copyOf(productIds);
	}

	/** Same as the full parse, listing the active occurrences (spec 012 callers). */
	public static OccurrenceFilter parse(String page, String size, List<String> types, List<String> statuses,
			List<String> productIds, String from, String to, String text) {
		return parse(page, size, types, statuses, productIds, from, to, text, null);
	}

	/**
	 * Parses the raw query parameters; every invalid one is reported together in a 400 with field errors.
	 * {@code archived} is {@code true} for the archived occurrences only, absent or {@code false} for the active ones
	 * (spec 014).
	 */
	public static OccurrenceFilter parse(String page, String size, List<String> types, List<String> statuses,
			List<String> productIds, String from, String to, String text, String archived) {
		Map<String, String> errors = new LinkedHashMap<>();
		int pageNumber = parsePage(page, errors);
		int pageSize = parseSize(size, errors);

		Set<OccurrenceType> typeSet = EnumSet.noneOf(OccurrenceType.class);
		for (String value : values(types)) {
			OccurrenceType.parse(value).ifPresentOrElse(typeSet::add,
					() -> errors.putIfAbsent("type", "Tipo desconhecido. Use ERROR, WARNING ou INFO."));
		}
		Set<OccurrenceStatus> statusSet = EnumSet.noneOf(OccurrenceStatus.class);
		for (String value : values(statuses)) {
			OccurrenceStatus.parse(value).ifPresentOrElse(statusSet::add,
					() -> errors.putIfAbsent("status", "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA."));
		}
		Set<String> productSet = new LinkedHashSet<>();
		for (String value : values(productIds)) {
			productSet.add(value.trim());
		}

		Instant fromInstant = parseInstant("from", from, errors);
		Instant toInstant = parseInstant("to", to, errors);
		if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
			errors.putIfAbsent("to", "O fim do período precisa ser depois do início.");
		}

		String search = text == null ? null : text.trim();
		if (search != null && search.isEmpty()) search = null;
		if (search != null && search.length() > MAX_TEXT) {
			errors.put("q", "A busca aceita até 200 caracteres.");
		}

		boolean archivedOnly = false;
		if (archived != null && !archived.isBlank()) {
			String value = archived.trim();
			if (value.equalsIgnoreCase("true")) archivedOnly = true;
			else if (!value.equalsIgnoreCase("false")) errors.put("archived", "Use true ou false.");
		}

		if (!errors.isEmpty()) throw new FieldValidationException(errors);
		return new OccurrenceFilter(pageNumber, pageSize, typeSet, statusSet, productSet, fromInstant, toInstant,
				search, archivedOnly);
	}

	private static List<String> values(List<String> raw) {
		return raw == null ? List.of() : raw.stream().filter(value -> value != null && !value.isBlank()).toList();
	}

	private static int parsePage(String raw, Map<String, String> errors) {
		if (raw == null || raw.isBlank()) return 1;
		try {
			int value = Integer.parseInt(raw.trim());
			if (value >= 1) return value;
		}
		catch (NumberFormatException ignored) {
			// reported below
		}
		errors.put("page", "A página começa em 1.");
		return 1;
	}

	private static int parseSize(String raw, Map<String, String> errors) {
		if (raw == null || raw.isBlank()) return DEFAULT_SIZE;
		try {
			int value = Integer.parseInt(raw.trim());
			if (SIZES.contains(value)) return value;
		}
		catch (NumberFormatException ignored) {
			// reported below
		}
		errors.put("size", "Use 10, 20 ou 50.");
		return DEFAULT_SIZE;
	}

	private static Instant parseInstant(String field, String raw, Map<String, String> errors) {
		if (raw == null || raw.isBlank()) return null;
		try {
			// An unencoded "+03:00" offset arrives as a space in the query string.
			return OffsetDateTime.parse(raw.trim().replace(' ', '+')).toInstant();
		}
		catch (DateTimeParseException exception) {
			errors.put(field, "Data inválida. Use o formato ISO-8601 com fuso.");
			return null;
		}
	}
}
