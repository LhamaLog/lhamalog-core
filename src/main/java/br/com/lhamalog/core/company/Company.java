package br.com.lhamalog.core.company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("Tenant")
public record Company(@Id String id, String clerkOrgId, Data data, Context context, Address address,
		Preferences preferences, CompanySettingsState settings, Logo logo, Instant updatedAt, String updatedByUserId,
		@Version Long version) {

	public record Data(String legalName, String tradeName, String document, LocalDate registrationDate, String size,
			String stateRegistration, String email, String sector, String description, String website) {}

	public record Context(String sector, String mission, String targetAudience, String differentiators, String language,
			List<String> environments, String criticalWindows, List<String> monitoredSystems, String description) {}

	public record Address(String street, String number, String complement, String district, String city, String state,
			String country, String postalCode, String reference, Boolean useAsFiscalAndCorrespondence) {}

	public record Preferences(String timezone, String language, String dateFormat, String timeFormat, String currency,
			Boolean dailySummary, Boolean aiHints, String dataInterval, String initialEnvironment, String logOrdering,
			Boolean onlyActiveProducts, String aiDetail, String aiTone, String aiPriority, Boolean suggestCause,
			Boolean suggestCorrectiveAction, Boolean suggestSimilar, Boolean notifyCriticalImmediately,
			Boolean notifyRecurringFailures, Boolean requireManagerMfa, Boolean allowApprovedIntegrations,
			Boolean maskSensitiveData) {}

	public record Logo(String fileName, String contentType, long sizeBytes, Instant updatedAt) {}

	public static Company empty(String organizationId) {
		return new Company(organizationId, organizationId, new Data("", "", "", null, "", "", "", "", "", ""),
				new Context("", "", "", "", "", List.of(), "", List.of(), ""),
				new Address("", "", "", "", "", "", "", "", "", null), null, null, null, null, null, null);
	}
}
