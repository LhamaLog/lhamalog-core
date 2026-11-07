package br.com.lhamalog.core.company;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

final class SettingsEcho {
	private static final Set<String> SYSTEMS = Set.of("API", "Backend", "Frontend", "Banco de Dados", "Workers", "Mobile",
			"Infraestrutura");

	record LogoInstruction(boolean delete, CompanyLogo store) {
		static LogoInstruction keep() { return new LogoInstruction(false, null); }
		static LogoInstruction clear() { return new LogoInstruction(true, null); }
		static LogoInstruction store(CompanyLogo logo) { return new LogoInstruction(false, logo); }
	}

	record Outcome(boolean present, CompanySettingsState state, LogoInstruction logo, Map<String, String> errors) {
		static Outcome absent() { return new Outcome(false, null, LogoInstruction.keep(), Map.of()); }
	}

	private SettingsEcho() {}

	static Outcome read(String tenantId, JsonNode settings) {
		if (settings == null || settings.isMissingNode() || settings.isNull()) {
			return Outcome.absent();
		}
		Map<String, String> errors = new LinkedHashMap<>();
		if (!settings.isObject()) {
			errors.put("settings", "Confira o preenchimento deste campo.");
			return new Outcome(true, null, LogoInstruction.keep(), errors);
		}
		JsonNode data = object(settings, "data", errors);
		JsonNode context = object(settings, "context", errors);
		JsonNode address = object(settings, "address", errors);
		JsonNode preferences = object(settings, "preferences", errors);
		String document = text(data, "document", 32, "settings.data.document", errors);
		if (!document.isBlank() && !CompanyProfiles.cnpj(document)) {
			errors.put("settings.data.document", "Informe um CNPJ válido.");
		}
		String email = text(data, "email", 200, "settings.data.email", errors);
		if (!email.isBlank() && !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			errors.put("settings.data.email", "Informe um e-mail válido.");
		}
		String website = text(data, "website", 300, "settings.data.website", errors);
		if (!website.isBlank() && !website.trim().matches("^https?://\\S+$")) {
			errors.put("settings.data.website", "Informe um site http ou https.");
		}
		String postal = text(address, "postalCode", 16, "settings.address.postalCode", errors);
		if (!postal.isBlank() && !postal.replaceAll("\\D", "").matches("\\d{8}")) {
			errors.put("settings.address.postalCode", "Informe um CEP com 8 dígitos.");
		}
		LogoInstruction logo = logo(tenantId, data, errors);
		CompanySettingsState state = new CompanySettingsState(
				new CompanySettingsState.Data(text(data, "legalName", 200, "settings.data.legalName", errors),
						text(data, "tradeName", 200, "settings.data.tradeName", errors), document, email,
						text(data, "stateRegistration", 40, "settings.data.stateRegistration", errors),
						text(data, "sector", 200, "settings.data.sector", errors),
						text(data, "description", 500, "settings.data.description", errors), website,
						text(data, "size", 80, "settings.data.size", errors),
						text(data, "timeZone", 80, "settings.data.timeZone", errors),
						text(data, "language", 80, "settings.data.language", errors),
						logo.store() == null ? null
								: new CompanySettingsState.Logo(logo.store().fileName(), logo.store().contentType())),
				new CompanySettingsState.Context(names(context, "environmentTypes", 30, 80, "settings.context.environmentTypes", errors),
						systems(context, errors), text(context, "criticalWindows", 200, "settings.context.criticalWindows", errors),
						text(context, "description", 2000, "settings.context.description", errors)),
				new CompanySettingsState.Address(postal, text(address, "country", 60, "settings.address.country", errors),
						text(address, "street", 200, "settings.address.street", errors),
						text(address, "number", 30, "settings.address.number", errors),
						text(address, "complement", 200, "settings.address.complement", errors),
						text(address, "district", 200, "settings.address.district", errors),
						text(address, "city", 200, "settings.address.city", errors),
						text(address, "state", 40, "settings.address.state", errors),
						text(address, "reference", 200, "settings.address.reference", errors),
						flag(address, "fiscalAddress", "settings.address.fiscalAddress", errors)),
				preferences(preferences, errors));
		if (logo.delete()) {
			state = state.withLogo(null);
		}
		return new Outcome(true, state, logo, errors);
	}

	private static CompanySettingsState.Preferences preferences(JsonNode node, Map<String, String> errors) {
		JsonNode telemetry = object(node, "telemetry", errors);
		JsonNode ai = object(node, "ai", errors);
		JsonNode notifications = object(node, "notifications", errors);
		JsonNode security = object(node, "security", errors);
		return new CompanySettingsState.Preferences(text(node, "currency", 40, "settings.preferences.currency", errors),
				text(node, "dateFormat", 40, "settings.preferences.dateFormat", errors),
				text(node, "timeFormat", 40, "settings.preferences.timeFormat", errors),
				flag(node, "dailySummary", "settings.preferences.dailySummary", errors),
				flag(node, "autoSuggestions", "settings.preferences.autoSuggestions", errors),
				new CompanySettingsState.Telemetry(text(telemetry, "interval", 80, "settings.preferences.telemetry.interval", errors),
						text(telemetry, "initialEnvironment", 80, "settings.preferences.telemetry.initialEnvironment", errors),
						text(telemetry, "logOrder", 80, "settings.preferences.telemetry.logOrder", errors),
						flag(telemetry, "activeProductsOnly", "settings.preferences.telemetry.activeProductsOnly", errors)),
				new CompanySettingsState.Ai(text(ai, "detailLevel", 80, "settings.preferences.ai.detailLevel", errors),
						text(ai, "tone", 80, "settings.preferences.ai.tone", errors),
						text(ai, "priority", 80, "settings.preferences.ai.priority", errors),
						flag(ai, "probableCause", "settings.preferences.ai.probableCause", errors),
						flag(ai, "correctiveAction", "settings.preferences.ai.correctiveAction", errors),
						flag(ai, "relateSimilar", "settings.preferences.ai.relateSimilar", errors)),
				new CompanySettingsState.Notifications(
						flag(notifications, "criticalImmediate", "settings.preferences.notifications.criticalImmediate", errors),
						flag(notifications, "recurringFailures", "settings.preferences.notifications.recurringFailures", errors)),
				new CompanySettingsState.Security(flag(security, "requireMfa", "settings.preferences.security.requireMfa", errors),
						flag(security, "approvedIntegrations", "settings.preferences.security.approvedIntegrations", errors),
						flag(security, "maskSensitive", "settings.preferences.security.maskSensitive", errors)));
	}

	private static LogoInstruction logo(String tenantId, JsonNode data, Map<String, String> errors) {
		if (data == null || !data.has("logo") || data.get("logo").isNull()) {
			return data != null && data.has("logo") ? LogoInstruction.clear() : LogoInstruction.keep();
		}
		JsonNode logo = data.get("logo");
		if (!logo.isObject()) {
			errors.put("settings.data.logo", "Confira o preenchimento deste campo.");
			return LogoInstruction.keep();
		}
		String fileName = text(logo, "fileName", 200, "settings.data.logo.fileName", errors);
		String mediaType = text(logo, "mediaType", 80, "settings.data.logo.mediaType", errors);
		String dataBase64 = text(logo, "dataBase64", Integer.MAX_VALUE, "settings.data.logo.dataBase64", errors);
		if (fileName.isBlank() || mediaType.isBlank() || dataBase64.isBlank() || fileName.indexOf('/') >= 0
				|| fileName.indexOf('\\') >= 0 || dataBase64.length() > (LogoFiles.MAX_BYTES / 3) * 4 + 16) {
			errors.put("settings.data.logo", "Envie o nome, o tipo e o conteúdo do logotipo.");
			return LogoInstruction.keep();
		}
		byte[] content;
		try {
			content = Base64.getMimeDecoder().decode(dataBase64);
		}
		catch (IllegalArgumentException exception) {
			errors.put("settings.data.logo", "O conteúdo do logotipo não é Base64 válido.");
			return LogoInstruction.keep();
		}
		try {
			LogoFiles.verifyAccepted(content, mediaType);
		}
		catch (br.com.lhamalog.core.shared.FieldValidationException exception) {
			errors.putAll(exception.fieldErrors());
			return LogoInstruction.keep();
		}
		return LogoInstruction.store(new CompanyLogo(tenantId, mediaType, fileName, content, dataBase64, Instant.now()));
	}

	private static List<String> systems(JsonNode context, Map<String, String> errors) {
		List<String> values = names(context, "monitoredSystems", 12, 40, "settings.context.monitoredSystems", errors);
		for (String value : values) {
			if (!SYSTEMS.contains(value)) {
				errors.put("settings.context.monitoredSystems",
						"Escolha apenas API, Backend, Frontend, Banco de Dados, Workers, Mobile ou Infraestrutura.");
			}
		}
		return values;
	}

	private static List<String> names(JsonNode node, String key, int maxItems, int maxLength, String path,
			Map<String, String> errors) {
		if (node == null || !node.has(key) || node.get(key).isNull()) {
			return List.of();
		}
		JsonNode value = node.get(key);
		if (!value.isArray()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return List.of();
		}
		if (value.size() > maxItems) {
			errors.put(path, "O texto excede o limite permitido.");
		}
		List<String> items = new ArrayList<>();
		for (JsonNode item : value) {
			if (!item.isTextual()) {
				errors.put(path, "Confira o preenchimento deste campo.");
				continue;
			}
			String text = item.asText();
			if (text.length() > maxLength || text.chars().anyMatch(ch -> ch < 32)) {
				errors.put(path, "O texto excede o limite permitido.");
				continue;
			}
			items.add(text);
		}
		return List.copyOf(items);
	}

	private static JsonNode object(JsonNode node, String key, Map<String, String> errors) {
		if (node == null || !node.has(key) || node.get(key).isNull()) {
			return null;
		}
		if (!node.get(key).isObject()) {
			errors.put(key.startsWith("settings.") ? key : "settings." + key, "Confira o preenchimento deste campo.");
			return null;
		}
		return node.get(key);
	}

	private static String text(JsonNode node, String key, int max, String path, Map<String, String> errors) {
		if (node == null || !node.has(key) || node.get(key).isNull()) {
			return "";
		}
		if (!node.get(key).isTextual()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return "";
		}
		String value = node.get(key).asText();
		if (value.length() > max || value.chars().anyMatch(ch -> ch < 32 && ch != '\n' && ch != '\r' && ch != '\t')) {
			errors.put(path, "O texto excede o limite permitido.");
		}
		return value;
	}

	private static boolean flag(JsonNode node, String key, String path, Map<String, String> errors) {
		if (node == null || !node.has(key) || node.get(key).isNull()) {
			return false;
		}
		if (!node.get(key).isBoolean()) {
			errors.put(path, "Confira o preenchimento deste campo.");
			return false;
		}
		return node.get(key).asBoolean();
	}
}
