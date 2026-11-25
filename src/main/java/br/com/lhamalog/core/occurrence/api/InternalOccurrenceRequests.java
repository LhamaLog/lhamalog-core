package br.com.lhamalog.core.occurrence.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import br.com.lhamalog.core.occurrence.application.AnalysisSummary;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.DeliveryKey;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.Finding;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary.OccurrenceKey;
import br.com.lhamalog.core.occurrence.application.InternalApiException;
import tools.jackson.databind.JsonNode;

/**
 * Strict reader of the request bodies of contract DN-009-2 ({@code ConsultaAchado} and {@code ResumoAnalise}). The body
 * is read as a tree so that unknown fields, wrong JSON types and contract rules all become 422 {@code DADOS_INVALIDOS}
 * (spec 013, research R5); only an unreadable body or a non-object root is 400. Nothing read here is logged.
 */
public final class InternalOccurrenceRequests {

	static final Set<String> TYPES = Set.of("ERROR", "WARNING", "INFO");

	private static final Pattern UTC_INSTANT = Pattern.compile(".*Z$");

	private static final Pattern EXEC_ID = Pattern.compile(
			"^[VM][0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private static final Set<String> LOOKUP_FIELDS = Set.of("tenantId", "productId", "assinatura", "tipo");

	private static final Set<String> SUMMARY_FIELDS = Set.of("tenantId", "productId", "batchId", "execId",
			"versaoRegras", "versaoCatalogo", "identificacaoRegras", "contadores", "achados");

	private static final Set<String> SUMMARY_REQUIRED = Set.of("tenantId", "productId", "batchId", "versaoRegras",
			"contadores", "achados");

	private static final Set<String> RULES_FIELDS = Set.of("versaoUnica", "somenteNivel", "catalogoEntrou");

	private static final Set<String> COUNTER_FIELDS = Set.of("linhasProcessadas", "erros", "warnings", "informacao",
			"dadosSensiveisMascarados");

	private static final Set<String> FINDING_REQUIRED = Set.of("tipo", "assinatura", "hostName", "contagem",
			"primeiraOcorrencia", "ultimaOcorrencia", "recorrente", "linhas", "revisaoConsultada", "titulo", "resumo",
			"numeroLinha", "camposExtraido", "dadosSensiveis");

	private static final Set<String> FINDING_FIELDS = union(FINDING_REQUIRED,
			Set.of("traceId", "correlationId", "moduloId"));

	private InternalOccurrenceRequests() {
	}

	/** Body of {@code POST /internal/v1/ocorrencias/consultas}. */
	public record Lookup(String tenantId, String productId, OccurrenceKey key) {}

	public static Lookup readLookup(JsonNode body) {
		requireObjectRoot(body);
		onlyFields(body, LOOKUP_FIELDS, LOOKUP_FIELDS);
		return new Lookup(identifier(body, "tenantId"), identifier(body, "productId"),
				new OccurrenceKey(identifier(body, "assinatura"), type(body.get("tipo"))));
	}

	/** Body of {@code POST /internal/v1/resumos-analise}. */
	public static AnalysisSummary readSummary(JsonNode body, int maxFindings) {
		requireObjectRoot(body);
		onlyFields(body, SUMMARY_FIELDS, SUMMARY_REQUIRED);
		String rulesVersion = identifier(body, "versaoRegras");
		String execId = null;
		if (body.has("execId")) {
			execId = identifier(body, "execId");
			if (!EXEC_ID.matcher(execId).matches()) throw InternalApiException.invalidData();
		}
		if (body.has("versaoCatalogo")) identifier(body, "versaoCatalogo");
		if (body.has("identificacaoRegras")) rulesIdentification(body.get("identificacaoRegras"), rulesVersion);
		counters(body.get("contadores"));
		JsonNode findings = body.get("achados");
		if (findings == null || !findings.isArray()) throw InternalApiException.invalidData();
		if (findings.size() > maxFindings) {
			throw new InternalApiException(HttpStatus.UNPROCESSABLE_CONTENT, InternalApiException.SUMMARY_TOO_LARGE);
		}
		List<Finding> read = new ArrayList<>();
		Set<OccurrenceKey> keys = new HashSet<>();
		for (JsonNode finding : findings) {
			Finding value = finding(finding);
			if (!keys.add(value.key())) throw InternalApiException.invalidData();
			read.add(value);
		}
		DeliveryKey key = new DeliveryKey(identifier(body, "tenantId"), identifier(body, "productId"),
				identifier(body, "batchId"), execId);
		return new AnalysisSummary(key, rulesVersion, read);
	}

	private static Finding finding(JsonNode node) {
		if (node == null || !node.isObject()) throw InternalApiException.invalidData();
		onlyFields(node, FINDING_FIELDS, FINDING_REQUIRED);
		long count = integer(node.get("contagem"));
		if (count < 1) throw InternalApiException.invalidData();
		Instant first = instant(node.get("primeiraOcorrencia"));
		Instant last = instant(node.get("ultimaOcorrencia"));
		if (first.isAfter(last)) throw InternalApiException.invalidData();
		JsonNode recurring = node.get("recorrente");
		if (!recurring.isBoolean()) throw InternalApiException.invalidData();
		Integer lineNumber = null;
		if (!node.get("numeroLinha").isNull()) {
			long value = integer(node.get("numeroLinha"));
			if (value < 1 || value > Integer.MAX_VALUE) throw InternalApiException.invalidData();
			lineNumber = (int) value;
		}
		JsonNode sensitive = node.get("dadosSensiveis");
		if (!sensitive.isNull() && !sensitive.isBoolean()) throw InternalApiException.invalidData();
		return new Finding(new OccurrenceKey(identifier(node, "assinatura"), type(node.get("tipo"))),
				identifier(node, "hostName"), count, first, last, recurring.booleanValue(), lines(node.get("linhas")),
				nullableIdentifier(node.get("revisaoConsultada")), nullableText(node.get("titulo")),
				nullableText(node.get("resumo")), lineNumber, extractedFields(node.get("camposExtraido")),
				sensitive.isNull() ? null : sensitive.booleanValue(), optionalIdentifier(node, "traceId"),
				optionalIdentifier(node, "correlationId"), optionalIdentifier(node, "moduloId"));
	}

	private static void rulesIdentification(JsonNode node, String rulesVersion) {
		if (node == null || !node.isObject()) throw InternalApiException.invalidData();
		onlyFields(node, RULES_FIELDS, RULES_FIELDS);
		if (!identifier(node, "versaoUnica").equals(rulesVersion)) throw InternalApiException.invalidData();
		if (!node.get("somenteNivel").isBoolean() || !node.get("catalogoEntrou").isBoolean()) {
			throw InternalApiException.invalidData();
		}
	}

	private static void counters(JsonNode node) {
		if (node == null || !node.isObject()) throw InternalApiException.invalidData();
		onlyFields(node, COUNTER_FIELDS, COUNTER_FIELDS);
		Map<String, Long> values = new LinkedHashMap<>();
		for (String name : COUNTER_FIELDS) {
			long value = integer(node.get(name));
			if (value < 0) throw InternalApiException.invalidData();
			values.put(name, value);
		}
		long classes = values.get("erros") + values.get("warnings") + values.get("informacao");
		if (values.get("linhasProcessadas") != classes) throw InternalApiException.invalidData();
	}

	private static List<String> lines(JsonNode node) {
		if (node == null || !node.isArray()) throw InternalApiException.invalidData();
		List<String> lines = new ArrayList<>();
		for (JsonNode line : node) {
			if (!line.isString()) throw InternalApiException.invalidData();
			lines.add(line.stringValue());
		}
		return lines;
	}

	private static Map<String, String> extractedFields(JsonNode node) {
		if (node.isNull()) return null;
		if (!node.isObject()) throw InternalApiException.invalidData();
		Map<String, String> fields = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> field : node.properties()) {
			if (!field.getValue().isString() || field.getKey().isEmpty()) throw InternalApiException.invalidData();
			fields.put(field.getKey(), field.getValue().stringValue());
		}
		return fields;
	}

	private static void requireObjectRoot(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new InternalApiException(HttpStatus.BAD_REQUEST, InternalApiException.INVALID_REQUEST);
		}
	}

	private static void onlyFields(JsonNode node, Set<String> allowed, Set<String> required) {
		for (String name : node.propertyNames()) {
			if (!allowed.contains(name)) throw InternalApiException.invalidData();
		}
		for (String name : required) {
			if (!node.has(name)) throw InternalApiException.invalidData();
		}
	}

	private static String identifier(JsonNode node, String name) {
		String value = nullableIdentifier(node.get(name));
		if (value == null) throw InternalApiException.invalidData();
		return value;
	}

	private static String optionalIdentifier(JsonNode node, String name) {
		return node.has(name) ? identifier(node, name) : null;
	}

	/** Null for JSON null; otherwise a non-blank string. */
	private static String nullableIdentifier(JsonNode node) {
		if (node == null) throw InternalApiException.invalidData();
		if (node.isNull()) return null;
		if (!node.isString() || node.stringValue().isBlank()) throw InternalApiException.invalidData();
		return node.stringValue();
	}

	private static String nullableText(JsonNode node) {
		if (node.isNull()) return null;
		if (!node.isString()) throw InternalApiException.invalidData();
		return node.stringValue();
	}

	private static String type(JsonNode node) {
		if (node == null || !node.isString() || !TYPES.contains(node.stringValue())) {
			throw InternalApiException.invalidData();
		}
		return node.stringValue();
	}

	private static long integer(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) throw InternalApiException.invalidData();
		return node.longValue();
	}

	private static Instant instant(JsonNode node) {
		if (node == null || !node.isString() || !UTC_INSTANT.matcher(node.stringValue()).matches()) {
			throw InternalApiException.invalidData();
		}
		try {
			return Instant.parse(node.stringValue());
		}
		catch (DateTimeParseException invalid) {
			throw InternalApiException.invalidData();
		}
	}

	private static Set<String> union(Set<String> first, Set<String> second) {
		Set<String> all = new HashSet<>(first);
		all.addAll(second);
		return Set.copyOf(all);
	}

}
