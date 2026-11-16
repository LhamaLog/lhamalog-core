package br.com.lhamalog.core.usage.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Lifecycle status of a tenant/product plan subscription (ADR_163 / LLT-183).
 *
 * <p>Mongo and JSON persist/emit English names ({@code ACTIVE}, {@code SUSPENDED}, {@code DELINQUENT}).
 * Legacy Portuguese spellings ({@code ATIVA}, {@code SUSPENSA}, {@code INADIMPLENTE}) are accepted on
 * read (case-insensitive) via {@link #fromPersisted(String)} and {@link #fromJson(String)}.
 */
public enum SubscriptionStatus {
	ACTIVE,
	SUSPENDED,
	DELINQUENT;

	/**
	 * Maps a persisted or API status string to the English enum. Accepts both English and legacy
	 * Portuguese spellings, case-insensitive.
	 */
	public static SubscriptionStatus fromPersisted(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("subscription status must not be blank");
		}
		return switch (raw.trim().toUpperCase(Locale.ROOT)) {
			case "ACTIVE", "ATIVA" -> ACTIVE;
			case "SUSPENDED", "SUSPENSA" -> SUSPENDED;
			case "DELINQUENT", "INADIMPLENTE" -> DELINQUENT;
			default -> throw new IllegalArgumentException("unknown subscription status: " + raw);
		};
	}

	@JsonCreator
	public static SubscriptionStatus fromJson(String raw) {
		return fromPersisted(raw);
	}

	@JsonValue
	public String toJson() {
		return name();
	}
}
