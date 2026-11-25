package br.com.lhamalog.core.notificationrole.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Notification role of a company (PapelNotificacao, ADR_134/136). It only decides who receives which alerts; it grants
 * no access (USID_052). Severities, alert types and members are lists inside the role (clarify C5, ADR_142).
 *
 * @param defaultKey which of the five defaults this is, or null for a custom role
 * @param version optimistic lock; renames and list changes must send the version they read
 */
public record NotificationRole(String id, String tenantId, String name, String normalizedName, DefaultRole defaultKey,
		boolean isDefault, boolean immutable, List<Severity> severities, List<AlertType> alertTypes,
		List<String> memberUserIds, boolean deleted, Instant deletedAt, Instant createdAt, Instant updatedAt,
		Long version) {

	public static final int NAME_MAX_LENGTH = 60;

	public static final int MAX_MEMBERS = 200;

	public NotificationRole {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(tenantId, "tenantId");
		severities = List.copyOf(severities);
		alertTypes = List.copyOf(alertTypes);
		memberUserIds = List.copyOf(memberUserIds);
	}

	/** Same normalization as product names (LLT-177): trimmed and lower case with {@link Locale#ROOT}. */
	public static String normalize(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	public static NotificationRole custom(String id, String tenantId, String name, List<Severity> severities,
			List<AlertType> alertTypes, Instant now) {
		return new NotificationRole(id, tenantId, name, normalize(name), null, false, false, severities, alertTypes,
				List.of(), false, null, now, now, null);
	}

	public NotificationRole renamed(String newName, Instant now) {
		return new NotificationRole(id, tenantId, newName, normalize(newName), defaultKey, isDefault, immutable,
				severities, alertTypes, memberUserIds, deleted, deletedAt, createdAt, now, version);
	}

	public NotificationRole withSeverities(List<Severity> newSeverities, Instant now) {
		return new NotificationRole(id, tenantId, name, normalizedName, defaultKey, isDefault, immutable, newSeverities,
				alertTypes, memberUserIds, deleted, deletedAt, createdAt, now, version);
	}

	public NotificationRole withAlertTypes(List<AlertType> newAlertTypes, Instant now) {
		return new NotificationRole(id, tenantId, name, normalizedName, defaultKey, isDefault, immutable, severities,
				newAlertTypes, memberUserIds, deleted, deletedAt, createdAt, now, version);
	}

	public NotificationRole withMembers(List<String> newMembers, Instant now) {
		return new NotificationRole(id, tenantId, name, normalizedName, defaultKey, isDefault, immutable, severities,
				alertTypes, newMembers, deleted, deletedAt, createdAt, now, version);
	}

	/** Logical and final deletion (ADR_139): the role leaves every list and its id answers 404. */
	public NotificationRole deletedAt(Instant now) {
		return new NotificationRole(id, tenantId, name, normalizedName, defaultKey, isDefault, immutable, severities,
				alertTypes, memberUserIds, true, now, createdAt, now, version);
	}

	/** Same role with the version the client says it read, so the save fails if someone changed it meanwhile. */
	public NotificationRole expecting(long expectedVersion) {
		return new NotificationRole(id, tenantId, name, normalizedName, defaultKey, isDefault, immutable, severities,
				alertTypes, memberUserIds, deleted, deletedAt, createdAt, updatedAt, expectedVersion);
	}

}
