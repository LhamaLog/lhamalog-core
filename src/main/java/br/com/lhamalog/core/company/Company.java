package br.com.lhamalog.core.company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Company data (Empresa) of a tenant. It lives in the tenant document itself (ADR_121: the tenant is the company), next
 * to the fields owned by the tenant module (name, status). {@code id} is the internal tenant id, never the Clerk
 * organization id. Writes must be partial updates of the company fields only; never save this record as a whole
 * document (see {@link CompanyService}). {@code version} is null until the first save and is checked by hand.
 */
@Document(CompanyService.TENANT_COLLECTION)
public record Company(@Id String id, String clerkOrgId, Data data, Context context, Address address,
		Preferences preferences, CompanySettingsState settings, Logo logo, Instant updatedAt, String updatedByUserId,
		Long version) {

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

	/** A tenant whose company was never saved (or was removed): every company part empty. */
	public static Company empty(String tenantId, String clerkOrgId) {
		return new Company(tenantId, clerkOrgId, emptyData(), emptyContext(), emptyAddress(), null, null, null, null, null,
				null);
	}

	/** Fills the company parts never saved yet with empty values, keeping the other fields. */
	public Company withDefaults() {
		return new Company(id, clerkOrgId, data != null ? data : emptyData(), context != null ? context : emptyContext(),
				address != null ? address : emptyAddress(), preferences, settings, logo, updatedAt, updatedByUserId, version);
	}

	private static Data emptyData() {
		return new Data("", "", "", null, "", "", "", "", "", "");
	}

	private static Context emptyContext() {
		return new Context("", "", "", "", "", List.of(), "", List.of(), "");
	}

	private static Address emptyAddress() {
		return new Address("", "", "", "", "", "", "", "", "", null);
	}
}
