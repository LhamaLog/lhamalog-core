package br.com.lhamalog.core.profile;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

final class UserProfileRules {
	private static final int NAME_MAX = 200;
	private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");
	private static final Map<String, String> LANGUAGES = Map.of("Português (Brasil)", "pt-BR", "Portugues (Brasil)", "pt-BR");
	private static final Map<String, String> ZONES = Map.of("(UTC-03:00) Brasília", "America/Sao_Paulo",
			"(UTC-03:00) Brasilia", "America/Sao_Paulo");

	record Draft(String displayName, String language, String timezone, Long version, Map<String, String> errors) {}

	private UserProfileRules() {}

	static Draft read(JsonNode body) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (body == null || !body.isObject()) {
			errors.put("body", "Os dados enviados são inválidos.");
			return new Draft("", "", "", null, errors);
		}
		return new Draft(displayName(body, errors), language(body, errors), timezone(body, errors), version(body, errors),
				errors);
	}

	private static String displayName(JsonNode body, Map<String, String> errors) {
		String value = text(body, "displayName", errors);
		if (errors.containsKey("displayName")) {
			return "";
		}
		if (value.isBlank()) {
			errors.put("displayName", "Informe o nome.");
			return "";
		}
		if (value.length() > NAME_MAX || illegal(value)) {
			errors.put("displayName", "O texto excede o limite permitido.");
		}
		return value;
	}

	private static String language(JsonNode body, Map<String, String> errors) {
		String value = text(body, "language", errors);
		if (errors.containsKey("language")) {
			return "";
		}
		if (value.isBlank()) {
			errors.put("language", "Informe o idioma.");
			return "";
		}
		String normalized = LANGUAGES.getOrDefault(value, value);
		if (!LANGUAGE.matcher(normalized).matches()) {
			errors.put("language", "Informe um idioma válido.");
		}
		return normalized;
	}

	private static String timezone(JsonNode body, Map<String, String> errors) {
		String value = text(body, "timezone", errors);
		if (errors.containsKey("timezone")) {
			return "";
		}
		if (value.isBlank()) {
			errors.put("timezone", "Informe o fuso horário.");
			return "";
		}
		String normalized = ZONES.getOrDefault(value, value);
		try {
			return ZoneId.of(normalized).getId();
		}
		catch (RuntimeException exception) {
			errors.put("timezone", "Informe um fuso horário válido.");
			return value;
		}
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

	private static String text(JsonNode body, String key, Map<String, String> errors) {
		if (!body.has(key)) {
			errors.put(key, "Confira o preenchimento deste campo.");
			return "";
		}
		JsonNode value = body.get(key);
		if (value.isNull()) {
			return "";
		}
		if (!value.isTextual()) {
			errors.put(key, "Confira o preenchimento deste campo.");
			return "";
		}
		return value.asText().trim();
	}

	private static boolean illegal(String text) {
		return text.chars().anyMatch(ch -> ch < 32 && ch != '\t');
	}
}
