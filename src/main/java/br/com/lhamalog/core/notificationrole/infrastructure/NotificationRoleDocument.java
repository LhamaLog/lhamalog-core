package br.com.lhamalog.core.notificationrole.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB representation of a notification role. The Notification module reads this collection to deliver alerts
 * (contracts/api.md, last section).
 */
@Document(NotificationRoleDocument.COLLECTION)
public record NotificationRoleDocument(@Id String id, String tenantId, String name, String normalizedName,
		String defaultKey, boolean isDefault, boolean immutable, List<String> severities, List<String> alertTypes,
		List<String> memberUserIds, boolean deleted, Instant deletedAt, Instant createdAt, Instant updatedAt,
		@Version Long version) {

	public static final String COLLECTION = "NotificationRole";

	static final String ID = "_id";

	static final String TENANT_ID = "tenantId";

	static final String NAME = "name";

	static final String NORMALIZED_NAME = "normalizedName";

	static final String DEFAULT_KEY = "defaultKey";

	static final String IS_DEFAULT = "isDefault";

	static final String IMMUTABLE = "immutable";

	static final String SEVERITIES = "severities";

	static final String ALERT_TYPES = "alertTypes";

	static final String MEMBER_USER_IDS = "memberUserIds";

	static final String DELETED = "deleted";

	static final String DELETED_AT = "deletedAt";

	static final String CREATED_AT = "createdAt";

	static final String UPDATED_AT = "updatedAt";

	static final String VERSION = "version";

}
