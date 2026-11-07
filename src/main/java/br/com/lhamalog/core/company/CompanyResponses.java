package br.com.lhamalog.core.company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class CompanyResponses {
	private CompanyResponses() {}

	record Missing(String path, String label) {}

	record Completion(int filled, int total, int percent, List<Missing> missing, String message) {}

	record Summary(String legalName, String tradeName, String document, String stateRegistration, String sector,
			List<String> environments, String criticalWindows, List<String> monitoredSystems, int monitoredCount,
			String formattedAddress, boolean logoPresent, boolean verified, String verification, String accountStatus,
			Completion completion, Object metrics, Object ingestion) {}

	record Capabilities(boolean geocoding, boolean postalCodeLookup, boolean pdfExport, boolean cloudSync,
			boolean companyVerification, boolean logoUpload) {}

	record Limitation(String code, boolean stored, boolean enforced, String message) {}

	record Audit(Instant updatedAt, String updatedByUserId) {}

	record Shared(String sector, String timezone, String language) {}

	record DataView(String legalName, String tradeName, String document, LocalDate registrationDate, String size,
			String stateRegistration, String email, String sector, String description, String website, String timezone,
			String language, String organizationId) {}

	record LogoView(boolean present, String fileName, String contentType, long sizeBytes, Instant updatedAt) {}

	record SettingsLogo(String fileName, String mediaType, String dataBase64) {}

	record SettingsData(String legalName, String tradeName, String document, String email, String stateRegistration,
			String sector, String description, String website, String size, String timeZone, String language,
			SettingsLogo logo) {}

	record SettingsContext(List<String> environmentTypes, List<String> monitoredSystems, String criticalWindows,
			String description) {}

	record SettingsAddress(String postalCode, String country, String street, String number, String complement,
			String district, String city, String state, String reference, boolean fiscalAddress) {}

	record SettingsTelemetry(String interval, String initialEnvironment, String logOrder, boolean activeProductsOnly) {}

	record SettingsAi(String detailLevel, String tone, String priority, boolean probableCause, boolean correctiveAction,
			boolean relateSimilar) {}

	record SettingsNotifications(boolean criticalImmediate, boolean recurringFailures) {}

	record SettingsSecurity(boolean requireMfa, boolean approvedIntegrations, boolean maskSensitive) {}

	record SettingsPreferences(String currency, String dateFormat, String timeFormat, boolean dailySummary,
			boolean autoSuggestions, SettingsTelemetry telemetry, SettingsAi ai, SettingsNotifications notifications,
			SettingsSecurity security) {}

	record SettingsView(SettingsData data, SettingsContext context, SettingsAddress address, SettingsPreferences preferences) {}

	record CompanyResponse(String id, String clerkOrgId, String organizationId, DataView data, Company.Context context,
			Company.Address address, Company.Preferences preferences, Shared shared, LogoView logo, Summary summary,
			Capabilities capabilities, List<Limitation> limitations, Audit audit, SettingsView settings, Long version) {}

	static CompanyResponse from(Company company, CompanyLogo logoFile) {
		Company.Data data = company.data();
		Company.Context context = company.context();
		Company.Address address = company.address();
		Company.Preferences preferences = viewPreferences(company);
		String sector = first(data.sector(), context.sector());
		String language = first(preferences.language(), context.language());
		String timezone = blank(preferences.timezone());
		List<Missing> missing = missing(data, sector, address);
		int total = 10;
		int filled = total - missing.size();
		int percent = (int) Math.round(filled * 100.0 / total);
		String message = missing.isEmpty() ? "Cadastro completo."
				: "Faltam: " + String.join(", ", missing.stream().map(Missing::label).toList()) + ".";
		Completion completion = new Completion(filled, total, percent, missing, message);
		Company.Context visibleContext = new Company.Context(sector, blank(context.mission()), blank(context.targetAudience()),
				blank(context.differentiators()), language, list(context.environments()), blank(context.criticalWindows()),
				list(context.monitoredSystems()), blank(context.description()));
		return new CompanyResponse(company.id(), company.clerkOrgId(), company.clerkOrgId(),
				new DataView(blank(data.legalName()), blank(data.tradeName()), blank(data.document()), data.registrationDate(),
						blank(data.size()), blank(data.stateRegistration()), blank(data.email()), sector, blank(data.description()),
						blank(data.website()), timezone, language, company.clerkOrgId()),
				visibleContext, new Company.Address(blank(address.street()), blank(address.number()), blank(address.complement()),
						blank(address.district()), blank(address.city()), blank(address.state()), blank(address.country()),
						blank(address.postalCode()), blank(address.reference()),
						Boolean.TRUE.equals(address.useAsFiscalAndCorrespondence())),
				preferences, new Shared(sector, timezone, language), logo(company.logo()),
				new Summary(blank(data.legalName()), blank(data.tradeName()), blank(data.document()),
						blank(data.stateRegistration()), sector, list(context.environments()), blank(context.criticalWindows()),
						list(context.monitoredSystems()), list(context.monitoredSystems()).size(), format(address),
						company.logo() != null, false, "unavailable", missing.isEmpty() ? "COMPLETE" : "INCOMPLETE", completion,
						null, null),
				new Capabilities(false, false, false, false, false, true), limitations(preferences),
				new Audit(company.updatedAt(), company.updatedByUserId()), settings(company, logoFile), company.version());
	}

	private static SettingsView settings(Company company, CompanyLogo logoFile) {
		CompanySettingsState state = company.settings();
		if (state == null) {
			return synthesized(company, logoFile);
		}
		CompanySettingsState.Data data = state.data();
		CompanySettingsState.Preferences preferences = state.preferences();
		return new SettingsView(new SettingsData(data.legalName(), data.tradeName(), data.document(), data.email(),
				data.stateRegistration(), data.sector(), data.description(), data.website(), data.size(), data.timeZone(),
				data.language(), settingsLogo(data.logo(), logoFile)),
				new SettingsContext(dataList(state.context().environmentTypes()), dataList(state.context().monitoredSystems()),
						state.context().criticalWindows(), state.context().description()),
				new SettingsAddress(state.address().postalCode(), state.address().country(), state.address().street(),
						state.address().number(), state.address().complement(), state.address().district(),
						state.address().city(), state.address().state(), state.address().reference(),
						state.address().fiscalAddress()),
				new SettingsPreferences(preferences.currency(), preferences.dateFormat(), preferences.timeFormat(),
						preferences.dailySummary(), preferences.autoSuggestions(),
						new SettingsTelemetry(preferences.telemetry().interval(), preferences.telemetry().initialEnvironment(),
								preferences.telemetry().logOrder(), preferences.telemetry().activeProductsOnly()),
						new SettingsAi(preferences.ai().detailLevel(), preferences.ai().tone(), preferences.ai().priority(),
								preferences.ai().probableCause(), preferences.ai().correctiveAction(), preferences.ai().relateSimilar()),
						new SettingsNotifications(preferences.notifications().criticalImmediate(),
								preferences.notifications().recurringFailures()),
						new SettingsSecurity(preferences.security().requireMfa(), preferences.security().approvedIntegrations(),
								preferences.security().maskSensitive())));
	}

	private static SettingsView synthesized(Company company, CompanyLogo logoFile) {
		Company.Data data = company.data();
		Company.Context context = company.context();
		Company.Address address = company.address();
		SettingsLogo logo = company.logo() == null ? null : settingsLogo(new CompanySettingsState.Logo("", ""), logoFile);
		return new SettingsView(new SettingsData(blank(data.legalName()), blank(data.tradeName()), blank(data.document()),
				blank(data.email()), blank(data.stateRegistration()), first(data.sector(), context.sector()),
				first(data.description(), context.mission()), blank(data.website()), blank(data.size()), "",
				blank(context.language()), logo),
				new SettingsContext(list(context.environments()), list(context.monitoredSystems()), blank(context.criticalWindows()),
						first(context.description(), context.differentiators())),
				new SettingsAddress(blank(address.postalCode()), blank(address.country()), blank(address.street()),
						blank(address.number()), blank(address.complement()), blank(address.district()), blank(address.city()),
						blank(address.state()), blank(address.reference()), Boolean.TRUE.equals(address.useAsFiscalAndCorrespondence())),
				new SettingsPreferences("", "", "", false, false, new SettingsTelemetry("", "", "", false),
						new SettingsAi("", "", "", false, false, false), new SettingsNotifications(false, false),
						new SettingsSecurity(false, false, false)));
	}

	private static SettingsLogo settingsLogo(CompanySettingsState.Logo reference, CompanyLogo file) {
		if (reference == null || file == null || file.dataBase64() == null || file.dataBase64().isBlank()) {
			return null;
		}
		String fileName = file.fileName() == null || file.fileName().isBlank() ? reference.fileName() : file.fileName();
		String mediaType = file.contentType() == null || file.contentType().isBlank() ? reference.mediaType() : file.contentType();
		return new SettingsLogo(fileName, mediaType, file.dataBase64());
	}

	private static List<String> dataList(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	private static Company.Preferences viewPreferences(Company company) {
		Company.Preferences preferences = company.preferences();
		String language = preferences == null ? "" : blank(preferences.language());
		if (language.isBlank()) {
			language = blank(company.context().language());
		}
		if (preferences == null) {
			return new Company.Preferences("", language, "", "", "", false, false, "", "", "", false, "", "", "", false, false,
					false, false, false, false, false, false);
		}
		return new Company.Preferences(blank(preferences.timezone()), language, blank(preferences.dateFormat()),
				blank(preferences.timeFormat()), blank(preferences.currency()), flag(preferences.dailySummary()),
				flag(preferences.aiHints()), blank(preferences.dataInterval()), blank(preferences.initialEnvironment()),
				blank(preferences.logOrdering()), flag(preferences.onlyActiveProducts()), blank(preferences.aiDetail()),
				blank(preferences.aiTone()), blank(preferences.aiPriority()), flag(preferences.suggestCause()),
				flag(preferences.suggestCorrectiveAction()), flag(preferences.suggestSimilar()),
				flag(preferences.notifyCriticalImmediately()), flag(preferences.notifyRecurringFailures()),
				flag(preferences.requireManagerMfa()), flag(preferences.allowApprovedIntegrations()),
				flag(preferences.maskSensitiveData()));
	}

	private static List<Limitation> limitations(Company.Preferences preferences) {
		return List.of(
				limit("MANAGER_MFA", preferences.requireManagerMfa(), "A preferência não altera o MFA da Clerk."),
				limit("APPROVED_INTEGRATIONS", preferences.allowApprovedIntegrations(),
						"Não há gateway de integrações neste serviço."),
				limit("DATA_MASKING", preferences.maskSensitiveData(), "Não há emissão de relatórios neste serviço."),
				limit("ACTIVE_PRODUCTS_FILTER", preferences.onlyActiveProducts(),
						"A lista de produtos não usa esta preferência."),
				limit("DAILY_SUMMARY", preferences.dailySummary(), "Não há envio de resumo diário neste serviço."),
				limit("AI_HINTS", preferences.aiHints(), "Não há pipeline de IA neste serviço."),
				limit("AI_SUGGESTIONS", flag(preferences.suggestCause()) || flag(preferences.suggestCorrectiveAction())
						|| flag(preferences.suggestSimilar()), "As sugestões de IA não são executadas neste serviço."),
				limit("TELEMETRY_DEFAULTS", !blank(preferences.dataInterval()).isBlank()
						|| !blank(preferences.initialEnvironment()).isBlank() || !blank(preferences.logOrdering()).isBlank(),
						"Intervalo, ambiente inicial e ordenação não alteram a coleta."));
	}

	private static Limitation limit(String code, boolean stored, String message) {
		return new Limitation(code, stored, false, message);
	}

	private static List<Missing> missing(Company.Data data, String sector, Company.Address address) {
		List<Missing> missing = new ArrayList<>();
		add(missing, data.legalName(), "data.legalName", "Razão Social");
		add(missing, data.document(), "data.document", "CNPJ");
		add(missing, data.email(), "data.email", "Contato principal");
		add(missing, sector, "data.sector", "Ramo de atuação");
		add(missing, address.postalCode(), "address.postalCode", "CEP");
		add(missing, address.street(), "address.street", "Logradouro");
		add(missing, address.number(), "address.number", "Número");
		add(missing, address.district(), "address.district", "Bairro");
		add(missing, address.city(), "address.city", "Cidade");
		add(missing, address.state(), "address.state", "Estado");
		return List.copyOf(missing);
	}

	private static void add(List<Missing> missing, String value, String path, String label) {
		if (value == null || value.isBlank()) {
			missing.add(new Missing(path, label));
		}
	}

	private static LogoView logo(Company.Logo logo) {
		if (logo == null) {
			return new LogoView(false, "", "", 0, null);
		}
		return new LogoView(true, logo.fileName(), logo.contentType(), logo.sizeBytes(), logo.updatedAt());
	}

	private static String format(Company.Address address) {
		List<String> parts = new ArrayList<>();
		String line = join(", ", blank(address.street()), blank(address.number()));
		if (!blank(address.complement()).isBlank()) {
			line = line.isBlank() ? address.complement() : line + " - " + address.complement();
		}
		addPart(parts, line);
		addPart(parts, address.district());
		String city = join(" - ", blank(address.city()), blank(address.state()));
		addPart(parts, city);
		addPart(parts, address.postalCode());
		addPart(parts, address.country());
		return parts.isEmpty() ? "" : String.join(", ", parts);
	}

	private static void addPart(List<String> parts, String value) {
		if (value != null && !value.isBlank()) {
			parts.add(value);
		}
	}

	private static String join(String separator, String left, String right) {
		if (left.isBlank()) {
			return right;
		}
		if (right.isBlank()) {
			return left;
		}
		return left + separator + right;
	}

	private static List<String> list(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	private static boolean flag(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private static String first(String preferred, String fallback) {
		return preferred != null && !preferred.isBlank() ? preferred : blank(fallback);
	}

	private static String blank(String value) {
		return value == null ? "" : value;
	}
}
