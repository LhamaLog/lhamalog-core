package br.com.lhamalog.core.company;

import java.util.List;

public record CompanySettingsState(Data data, Context context, Address address, Preferences preferences) {
	public record Data(String legalName, String tradeName, String document, String email, String stateRegistration,
			String sector, String description, String website, String size, String timeZone, String language, Logo logo) {}

	public record Logo(String fileName, String mediaType) {}

	public record Context(List<String> environmentTypes, List<String> monitoredSystems, String criticalWindows,
			String description) {}

	public record Address(String postalCode, String country, String street, String number, String complement,
			String district, String city, String state, String reference, boolean fiscalAddress) {}

	public record Preferences(String currency, String dateFormat, String timeFormat, boolean dailySummary,
			boolean autoSuggestions, Telemetry telemetry, Ai ai, Notifications notifications, Security security) {}

	public record Telemetry(String interval, String initialEnvironment, String logOrder, boolean activeProductsOnly) {}

	public record Ai(String detailLevel, String tone, String priority, boolean probableCause, boolean correctiveAction,
			boolean relateSimilar) {}

	public record Notifications(boolean criticalImmediate, boolean recurringFailures) {}

	public record Security(boolean requireMfa, boolean approvedIntegrations, boolean maskSensitive) {}

	public CompanySettingsState withLogo(Logo logo) {
		return new CompanySettingsState(new Data(data.legalName(), data.tradeName(), data.document(), data.email(),
				data.stateRegistration(), data.sector(), data.description(), data.website(), data.size(), data.timeZone(),
				data.language(), logo), context, address, preferences);
	}
}
