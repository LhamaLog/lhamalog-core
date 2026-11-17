package br.com.lhamalog.core.company;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

final class CompanyProfiles {
	private static final int TEXT = 200;
	private static final Map<String, String> SYSTEMS = Map.of(
			"api", "API",
			"backend", "BACKEND",
			"frontend", "FRONTEND",
			"banco de dados", "DATABASE",
			"database", "DATABASE",
			"workers", "WORKERS");
	private static final Set<String> SYSTEM_CODES = Set.of("API", "BACKEND", "FRONTEND", "DATABASE", "WORKERS");
	private static final Map<String, String> ZONES = Map.of("(UTC-03:00) Brasília", "America/Sao_Paulo",
			"(UTC-03:00) Brasilia", "America/Sao_Paulo");
	private static final Map<String, String> LANGUAGES = Map.of("Português (Brasil)", "pt-BR", "Portugues (Brasil)", "pt-BR");
	private static final Map<String, String> INTERVALS = Map.of("Últimas 24 horas", "LAST_24_HOURS", "Ultimas 24 horas",
			"LAST_24_HOURS", "LAST_24_HOURS", "LAST_24_HOURS", "LAST_7_DAYS", "LAST_7_DAYS", "LAST_30_DAYS", "LAST_30_DAYS");
	private static final Map<String, String> ORDERING = Map.of("Mais recentes primeiro", "NEWEST_FIRST", "NEWEST_FIRST",
			"NEWEST_FIRST", "Mais antigos primeiro", "OLDEST_FIRST", "OLDEST_FIRST", "OLDEST_FIRST");
	private static final Map<String, String> DATES = Map.of("DD/MM/AAAA", "dd/MM/yyyy", "dd/MM/yyyy", "dd/MM/yyyy",
			"MM/DD/AAAA", "MM/dd/yyyy", "MM/dd/yyyy", "MM/dd/yyyy", "AAAA-MM-DD", "yyyy-MM-dd", "yyyy-MM-dd", "yyyy-MM-dd");
	private static final Map<String, String> HOURS = Map.of("24 horas", "24h", "24h", "24h", "12 horas", "12h", "12h", "12h");

	record Draft(Company company, Long requestedVersion, Map<String, String> errors, SettingsEcho.LogoInstruction logo) {}

	private CompanyProfiles() {}

	static Draft merge(Company current, JsonNode body) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (body == null || !body.isObject()) {
			errors.put("body", "Os dados enviados são inválidos.");
			return new Draft(current, null, errors, SettingsEcho.LogoInstruction.keep());
		}
		JsonNode data = requiredObject(body, "data", errors);
		JsonNode context = requiredObject(body, "context", errors);
		JsonNode address = requiredObject(body, "address", errors);
		JsonNode preferences = optionalObject(body, "preferences", errors);
		Long version = version(body, errors);
		Company.Data currentData = current.data();
		Company.Context currentContext = current.context();
		Company.Address currentAddress = current.address();
		Company.Preferences currentPreferences = current.preferences();
		String sector = sector(data, context, currentData.sector(), currentContext.sector(), errors);
		String language = choice(firstText(data, preferences, "language", currentLanguage(current), errors, "language"),
				LANGUAGES, "^[a-z]{2}(-[A-Z]{2})?$", "Informe um idioma válido.", errors,
				data != null && data.has("language") ? "data.language" : "preferences.language");
		String timezone = zone(firstText(data, preferences, "timezone", blank(currentPreferences == null ? null
				: currentPreferences.timezone()), errors, "timezone"), errors,
				data != null && data.has("timezone") ? "data.timezone" : "preferences.timezone");
		Company.Data mergedData = new Company.Data(
				text(data, "legalName", currentData.legalName(), TEXT, false, "data.legalName", errors),
				text(data, "tradeName", currentData.tradeName(), TEXT, false, "data.tradeName", errors),
				document(data, currentData.document(), errors),
				date(data, currentData.registrationDate(), errors),
				text(data, "size", currentData.size(), 80, false, "data.size", errors),
				text(data, "stateRegistration", currentData.stateRegistration(), 40, false, "data.stateRegistration", errors),
				email(data, currentData.email(), errors),
				sector,
				text(data, "description", currentData.description(), 500, true, "data.description", errors),
				website(data, currentData.website(), errors));
		Company.Context mergedContext = new Company.Context(sector,
				text(context, "mission", currentContext.mission(), 10000, true, "context.mission", errors),
				text(context, "targetAudience", currentContext.targetAudience(), 10000, true, "context.targetAudience", errors),
				text(context, "differentiators", currentContext.differentiators(), 10000, true, "context.differentiators", errors),
				language,
				chips(context, "environments", currentContext.environments(), errors),
				text(context, "criticalWindows", currentContext.criticalWindows(), TEXT, false, "context.criticalWindows", errors),
				systems(context, currentContext.monitoredSystems(), errors),
				text(context, "description", currentContext.description(), CompanyContextLimits.DESCRIPTION_MAX, true,
						"context.description", errors));
		Company.Address mergedAddress = new Company.Address(
				text(address, "street", currentAddress.street(), TEXT, false, "address.street", errors),
				text(address, "number", currentAddress.number(), 30, false, "address.number", errors),
				text(address, "complement", currentAddress.complement(), TEXT, false, "address.complement", errors),
				text(address, "district", currentAddress.district(), TEXT, false, "address.district", errors),
				text(address, "city", currentAddress.city(), TEXT, false, "address.city", errors),
				text(address, "state", currentAddress.state(), 40, false, "address.state", errors),
				text(address, "country", currentAddress.country(), 60, false, "address.country", errors),
				postal(address, currentAddress.postalCode(), errors),
				text(address, "reference", currentAddress.reference(), TEXT, false, "address.reference", errors),
				bool(address, "useAsFiscalAndCorrespondence", currentAddress.useAsFiscalAndCorrespondence(), errors));
		Company.Preferences mergedPreferences = preferences(preferences, currentPreferences, timezone, language, errors);
		SettingsEcho.Outcome echoed = SettingsEcho.read(current.id(), body.get("settings"));
		errors.putAll(echoed.errors());
		Company.Logo logo = current.logo();
		if (echoed.present() && echoed.logo().delete()) {
			logo = null;
		}
		if (echoed.logo().store() != null) {
			CompanyLogo stored = echoed.logo().store();
			logo = new Company.Logo(stored.fileName(), stored.contentType(), stored.content().length, stored.updatedAt());
		}
		CompanySettingsState settings = echoed.present() ? echoed.state() : current.settings();
		Company company = new Company(current.id(), current.clerkOrgId(), mergedData, mergedContext, mergedAddress,
				mergedPreferences, settings, logo, current.updatedAt(), current.updatedByUserId(), current.version());
		return new Draft(company, version, errors, echoed.logo());
	}

	private static void requireFilled(JsonNode data, JsonNode context, JsonNode address, Company.Data mergedData,
			Company.Context mergedContext, Company.Address mergedAddress, Map<String, String> errors) {
		boolean extendedData = hasAny(data, "email", "stateRegistration", "description", "website", "timezone", "language",
				"organizationId", "segment");
		boolean extendedContext = hasAny(context, "environments", "monitoredSystems", "criticalWindows", "description", "segment");
		boolean extendedAddress = hasAny(address, "reference", "useAsFiscalAndCorrespondence");
		if (extendedData) {
			required(mergedData.legalName(), "data.legalName", "Informe a razão social.", errors);
			required(mergedData.document(), "data.document", "Informe o CNPJ.", errors);
			required(mergedData.email(), "data.email", "Informe o e-mail principal.", errors);
		}
		if ((extendedData || extendedContext) && (mergedContext.sector() == null || mergedContext.sector().isBlank())) {
			errors.putIfAbsent("data.sector", "Informe o ramo de atuação.");
			errors.putIfAbsent("context.sector", "Informe o ramo de atuação.");
		}
		if (extendedAddress) {
			required(mergedAddress.postalCode(), "address.postalCode", "Informe o CEP.", errors);
			required(mergedAddress.street(), "address.street", "Informe o logradouro.", errors);
			required(mergedAddress.number(), "address.number", "Informe o número.", errors);
			required(mergedAddress.district(), "address.district", "Informe o bairro.", errors);
			required(mergedAddress.city(), "address.city", "Informe a cidade.", errors);
			required(mergedAddress.state(), "address.state", "Informe o estado.", errors);
		}
	}

	private static Company.Preferences preferences(JsonNode node, Company.Preferences current, String timezone,
			String language, Map<String, String> errors) {
		Company.Preferences base = current == null ? new Company.Preferences(null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null) : current;
		return new Company.Preferences(timezone, language,
				coded(node, "dateFormat", base.dateFormat(), DATES, "preferences.dateFormat", "Informe um formato de data válido.", errors),
				coded(node, "timeFormat", base.timeFormat(), HOURS, "preferences.timeFormat", "Informe um formato de hora válido.", errors),
				currency(node, base.currency(), errors),
				bool(node, "dailySummary", base.dailySummary(), errors, "preferences.dailySummary"),
				bool(node, "aiHints", base.aiHints(), errors, "preferences.aiHints"),
				coded(node, "dataInterval", base.dataInterval(), INTERVALS, "preferences.dataInterval",
						"Informe um intervalo de dados válido.", errors),
				text(node, "initialEnvironment", base.initialEnvironment(), 60, false, "preferences.initialEnvironment", errors),
				coded(node, "logOrdering", base.logOrdering(), ORDERING, "preferences.logOrdering",
						"Informe uma ordenação de logs válida.", errors),
				bool(node, "onlyActiveProducts", base.onlyActiveProducts(), errors, "preferences.onlyActiveProducts"),
				text(node, "aiDetail", base.aiDetail(), 60, false, "preferences.aiDetail", errors),
				text(node, "aiTone", base.aiTone(), 60, false, "preferences.aiTone", errors),
				text(node, "aiPriority", base.aiPriority(), 60, false, "preferences.aiPriority", errors),
				bool(node, "suggestCause", base.suggestCause(), errors, "preferences.suggestCause"),
				bool(node, "suggestCorrectiveAction", base.suggestCorrectiveAction(), errors, "preferences.suggestCorrectiveAction"),
				bool(node, "suggestSimilar", base.suggestSimilar(), errors, "preferences.suggestSimilar"),
				bool(node, "notifyCriticalImmediately", base.notifyCriticalImmediately(), errors,
						"preferences.notifyCriticalImmediately"),
				bool(node, "notifyRecurringFailures", base.notifyRecurringFailures(), errors, "preferences.notifyRecurringFailures"),
				bool(node, "requireManagerMfa", base.requireManagerMfa(), errors, "preferences.requireManagerMfa"),
				bool(node, "allowApprovedIntegrations", base.allowApprovedIntegrations(), errors,
						"preferences.allowApprovedIntegrations"),
				bool(node, "maskSensitiveData", base.maskSensitiveData(), errors, "preferences.maskSensitiveData"));
	}

	private static String sector(JsonNode data, JsonNode context, String currentData, String currentContext,
			Map<String, String> errors) {
		String current = firstNonBlank(currentData, currentContext);
		boolean dataSent = hasAny(data, "sector", "segment");
		boolean contextSent = hasAny(context, "sector", "segment");
		String fromData = dataSent ? alias(data, "data.sector", errors) : null;
		String fromContext = contextSent ? alias(context, "context.sector", errors) : null;
		if (fromData != null && fromContext != null && !fromData.isBlank() && !fromContext.isBlank()
				&& !fromData.equals(fromContext)) {
			errors.put("data.sector", "O ramo de atuação e o segmento precisam ser o mesmo valor.");
			errors.put("context.sector", "O ramo de atuação e o segmento precisam ser o mesmo valor.");
			return current;
		}
		if (fromData != null) {
			return fromData;
		}
		if (fromContext != null) {
			return fromContext;
		}
		return current;
	}

	private static String alias(JsonNode node, String path, Map<String, String> errors) {
		String sector = text(node, "sector", null, TEXT, false, path, errors);
		String segment = text(node, "segment", null, TEXT, false, path, errors);
		boolean hasSector = node.has("sector") && !node.get("sector").isNull();
		boolean hasSegment = node.has("segment") && !node.get("segment").isNull();
		if (hasSector && hasSegment && !sector.equals(segment)) {
			errors.put(path, "O ramo de atuação e o segmento precisam ser o mesmo valor.");
		}
		if (hasSector) {
			return sector;
		}
		return hasSegment ? segment : "";
	}

	private static String document(JsonNode node, String current, Map<String, String> errors) {
		String value = text(node, "document", current, 20, false, "data.document", errors);
		if (!nodeHas(node, "document") || value.isBlank()) {
			return value;
		}
		if (!cnpj(value)) {
			errors.put("data.document", "Informe um CNPJ válido.");
		}
		return value.trim();
	}

	private static String email(JsonNode node, String current, Map<String, String> errors) {
		String value = text(node, "email", current, TEXT, false, "data.email", errors);
		if (nodeHas(node, "email") && !value.isBlank() && !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			errors.put("data.email", "Informe um e-mail válido.");
		}
		return value;
	}

	private static String website(JsonNode node, String current, Map<String, String> errors) {
		String value = text(node, "website", current, 300, false, "data.website", errors);
		if (nodeHas(node, "website") && !value.isBlank() && !value.matches("^https?://\\S+$")) {
			errors.put("data.website", "Informe um site http ou https.");
		}
		return value;
	}

	private static String postal(JsonNode node, String current, Map<String, String> errors) {
		String value = text(node, "postalCode", current, 16, false, "address.postalCode", errors);
		if (nodeHas(node, "postalCode") && !value.isBlank() && !value.replaceAll("\\D", "").matches("\\d{8}")) {
			errors.put("address.postalCode", "Informe um CEP com 8 dígitos.");
		}
		return value;
	}

	private static LocalDate date(JsonNode node, LocalDate current, Map<String, String> errors) {
		if (!nodeHas(node, "registrationDate") || node.get("registrationDate").isNull()) {
			return nodeHas(node, "registrationDate") ? null : current;
		}
		if (!node.get("registrationDate").isTextual()) {
			errors.put("data.registrationDate", "Confira o preenchimento deste campo.");
			return current;
		}
		try {
			return LocalDate.parse(node.get("registrationDate").asText());
		}
		catch (DateTimeParseException exception) {
			errors.put("data.registrationDate", "Confira o preenchimento deste campo.");
			return current;
		}
	}

	private static String zone(String value, Map<String, String> errors, String path) {
		if (value.isBlank()) {
			return "";
		}
		String normalized = ZONES.getOrDefault(value, value);
		try {
			ZoneId.of(normalized);
			return normalized;
		}
		catch (RuntimeException exception) {
			errors.putIfAbsent(path, "Informe um fuso horário válido.");
			return value;
		}
	}

	private static String choice(String value, Map<String, String> aliases, String pattern, String message,
			Map<String, String> errors, String path) {
		if (value.isBlank()) {
			return "";
		}
		String normalized = aliases.getOrDefault(value, value);
		if (!normalized.matches(pattern)) {
			errors.putIfAbsent(path, message);
		}
		return normalized;
	}

	private static String currency(JsonNode node, String current, Map<String, String> errors) {
		String value = text(node, "currency", current, 20, false, "preferences.currency", errors);
		if (!nodeHas(node, "currency") || value.isBlank()) {
			return value;
		}
		if ("Real (BRL)".equals(value)) {
			return "BRL";
		}
		String code = value.toUpperCase(Locale.ROOT);
		if (!code.matches("[A-Z]{3}")) {
			errors.put("preferences.currency", "Informe uma moeda válida.");
			return value;
		}
		return code;
	}

	private static String coded(JsonNode node, String key, String current, Map<String, String> allowed, String path,
			String message, Map<String, String> errors) {
		String value = text(node, key, current, 40, false, path, errors);
		if (!nodeHas(node, key) || value.isBlank()) {
			return value;
		}
		String normalized = allowed.get(value);
		if (normalized == null) {
			errors.put(path, message);
			return value;
		}
		return normalized;
	}

	private static List<String> chips(JsonNode node, String key, List<String> current, Map<String, String> errors) {
		List<String> values = strings(node, key, current, 20, 40, "context.environments", errors);
		return values;
	}

	private static List<String> systems(JsonNode node, List<String> current, Map<String, String> errors) {
		if (!nodeHas(node, "monitoredSystems")) {
			return current == null ? List.of() : List.copyOf(current);
		}
		JsonNode value = node.get("monitoredSystems");
		if (value.isNull()) {
			return current == null ? List.of() : List.copyOf(current);
		}
		if (!value.isArray()) {
			errors.put("context.monitoredSystems", "Confira o preenchimento deste campo.");
			return current == null ? List.of() : List.copyOf(current);
		}
		LinkedHashSet<String> codes = new LinkedHashSet<>();
		for (JsonNode item : value) {
			if (!item.isTextual()) {
				errors.put("context.monitoredSystems", "Confira o preenchimento deste campo.");
				continue;
			}
			String code = SYSTEMS.get(item.asText().trim().toLowerCase(Locale.ROOT));
			if (code == null && SYSTEM_CODES.contains(item.asText().trim())) {
				code = item.asText().trim();
			}
			if (code == null) {
				errors.put("context.monitoredSystems", "Escolha apenas API, Backend, Frontend, Banco de Dados ou Workers.");
			}
			else {
				codes.add(code);
			}
		}
		return List.copyOf(codes);
	}

	private static List<String> strings(JsonNode node, String key, List<String> current, int maxItems, int maxLength,
			String path, Map<String, String> errors) {
		if (!nodeHas(node, key) || node.get(key).isNull()) {
			return current == null ? List.of() : List.copyOf(current);
		}
		JsonNode value = node.get(key);
		if (!value.isArray()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return current == null ? List.of() : List.copyOf(current);
		}
		if (value.size() > maxItems) {
			errors.put(path, "O texto excede o limite permitido.");
		}
		LinkedHashSet<String> items = new LinkedHashSet<>();
		for (JsonNode item : value) {
			if (!item.isTextual()) {
				errors.put(path, "Confira o preenchimento deste campo.");
				continue;
			}
			String text = item.asText().trim();
			if (text.isEmpty()) {
				continue;
			}
			if (text.length() > maxLength || illegal(text, false)) {
				errors.put(path, "O texto excede o limite permitido.");
				continue;
			}
			items.add(text);
		}
		return List.copyOf(items);
	}

	private static String firstText(JsonNode primary, JsonNode secondary, String key, String current,
			Map<String, String> errors, String name) {
		String left = nodeHas(primary, key) ? text(primary, key, "", TEXT, false, "data." + name, errors) : null;
		String right = nodeHas(secondary, key) ? text(secondary, key, "", TEXT, false, "preferences." + name, errors) : null;
		if (left != null && right != null && !left.equals(right) && !equivalent(name, left, right)) {
			errors.put("data." + name, "Use o mesmo valor em Dados da Empresa e Preferências.");
			errors.put("preferences." + name, "Use o mesmo valor em Dados da Empresa e Preferências.");
			return current;
		}
		if (left != null) {
			return left;
		}
		if (right != null) {
			return right;
		}
		return current;
	}

	private static boolean equivalent(String name, String left, String right) {
		if ("language".equals(name)) {
			return LANGUAGES.getOrDefault(left, left).equals(LANGUAGES.getOrDefault(right, right));
		}
		if ("timezone".equals(name)) {
			return ZONES.getOrDefault(left, left).equals(ZONES.getOrDefault(right, right));
		}
		return left.equals(right);
	}

	private static String currentLanguage(Company current) {
		String preferred = current.preferences() == null ? "" : blank(current.preferences().language());
		return firstNonBlank(preferred, current.context().language());
	}

	private static String text(JsonNode node, String key, String current, int max, boolean multiline, String path,
			Map<String, String> errors) {
		if (!nodeHas(node, key)) {
			return blank(current);
		}
		JsonNode value = node.get(key);
		if (value.isNull()) {
			return "";
		}
		if (!value.isTextual()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return blank(current);
		}
		String text = value.asText().trim();
		if (text.length() > max || illegal(text, multiline)) {
			errors.put(path, "O texto excede o limite permitido.");
		}
		return text;
	}

	private static Boolean bool(JsonNode node, String key, Boolean current, Map<String, String> errors) {
		return bool(node, key, current, errors, "address." + key);
	}

	private static Boolean bool(JsonNode node, String key, Boolean current, Map<String, String> errors, String path) {
		if (!nodeHas(node, key) || node.get(key).isNull()) {
			return current;
		}
		if (!node.get(key).isBoolean()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return current;
		}
		return node.get(key).asBoolean();
	}

	private static Long version(JsonNode body, Map<String, String> errors) {
		if (!body.has("version") || body.get("version").isNull()) {
			return null;
		}
		JsonNode value = body.get("version");
		if (!value.isIntegralNumber()) {
			errors.put("version", "Confira o preenchimento deste campo.");
			return null;
		}
		return value.longValue();
	}

	private static JsonNode requiredObject(JsonNode body, String key, Map<String, String> errors) {
		if (!body.has(key) || body.get(key).isNull()) {
			errors.put(key, "Confira o preenchimento deste campo.");
			return null;
		}
		if (!body.get(key).isObject()) {
			errors.put(key, "Confira o preenchimento deste campo.");
			return null;
		}
		return body.get(key);
	}

	private static JsonNode optionalObject(JsonNode body, String key, Map<String, String> errors) {
		if (!body.has(key) || body.get(key).isNull()) {
			return null;
		}
		if (!body.get(key).isObject()) {
			errors.put(key, "Confira o preenchimento deste campo.");
			return null;
		}
		return body.get(key);
	}

	private static boolean nodeHas(JsonNode node, String key) {
		return node != null && node.has(key);
	}

	private static boolean hasAny(JsonNode node, String... keys) {
		if (node == null) {
			return false;
		}
		for (String key : keys) {
			if (node.has(key)) {
				return true;
			}
		}
		return false;
	}

	private static void required(String value, String path, String message, Map<String, String> errors) {
		if (value == null || value.isBlank()) {
			errors.putIfAbsent(path, message);
		}
	}

	private static String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred;
		}
		return fallback == null ? "" : fallback;
	}

	private static String blank(String value) {
		return value == null ? "" : value;
	}

	private static boolean illegal(String text, boolean multiline) {
		return text.chars().anyMatch(ch -> ch < 32 && ch != '\t' && !(multiline && (ch == '\n' || ch == '\r')));
	}

	static boolean cnpj(String value) {
		String digits = value.replaceAll("\\D", "");
		if (!digits.matches("\\d{14}") || digits.chars().distinct().count() == 1) {
			return false;
		}
		int[] numbers = digits.chars().map(ch -> ch - '0').toArray();
		return numbers[12] == check(numbers, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2})
				&& numbers[13] == check(numbers, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
	}

	private static int check(int[] numbers, int[] weights) {
		int sum = 0;
		for (int index = 0; index < weights.length; index++) {
			sum += numbers[index] * weights[index];
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}
}
