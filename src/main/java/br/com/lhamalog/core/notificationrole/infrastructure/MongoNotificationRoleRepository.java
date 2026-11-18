package br.com.lhamalog.core.notificationrole.infrastructure;

import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.ALERT_TYPES;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.CREATED_AT;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.DEFAULT_KEY;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.DELETED;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.DELETED_AT;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.ID;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.IMMUTABLE;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.IS_DEFAULT;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.MEMBER_USER_IDS;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.NAME;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.NORMALIZED_NAME;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.SEVERITIES;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.TENANT_ID;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.UPDATED_AT;
import static br.com.lhamalog.core.notificationrole.infrastructure.NotificationRoleDocument.VERSION;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.notificationrole.application.AlertType;
import br.com.lhamalog.core.notificationrole.application.DefaultRole;
import br.com.lhamalog.core.notificationrole.application.NotificationRole;
import br.com.lhamalog.core.notificationrole.application.NotificationRoleRepository;
import br.com.lhamalog.core.notificationrole.application.Severity;

/** MongoDB adapter of {@link NotificationRoleRepository}, with the indexes of data-model.md. */
@Repository
public class MongoNotificationRoleRepository implements NotificationRoleRepository, InitializingBean {

	static final String NAME_INDEX = "notification_role_tenant_name";

	static final String DEFAULT_INDEX = "notification_role_tenant_default";

	static final String MEMBER_INDEX = "notification_role_tenant_member";

	private final MongoTemplate mongo;

	public MongoNotificationRoleRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public void afterPropertiesSet() {
		var indexes = mongo.indexOps(NotificationRoleDocument.class);
		// One active role per name: a deleted role frees its name (spec, edge cases).
		indexes.createIndex(new Index().on(TENANT_ID, Sort.Direction.ASC)
			.on(NORMALIZED_NAME, Sort.Direction.ASC)
			.unique()
			.partial(PartialIndexFilter.of(Criteria.where(DELETED).is(false)))
			.named(NAME_INDEX));
		// One document per default key, deleted ones included, so a deleted default never comes back.
		indexes.createIndex(new Index().on(TENANT_ID, Sort.Direction.ASC)
			.on(DEFAULT_KEY, Sort.Direction.ASC)
			.unique()
			.partial(PartialIndexFilter.of(new org.bson.Document(DEFAULT_KEY, new org.bson.Document("$type", "string"))))
			.named(DEFAULT_INDEX));
		indexes.createIndex(new Index().on(TENANT_ID, Sort.Direction.ASC)
			.on(MEMBER_USER_IDS, Sort.Direction.ASC)
			.named(MEMBER_INDEX));
	}

	@Override
	public void ensureDefaults(String tenantId, Instant now) {
		long present = mongo.count(Query.query(Criteria.where(TENANT_ID).is(tenantId).and(DEFAULT_KEY).ne(null)),
				NotificationRoleDocument.class);
		if (present >= DefaultRole.values().length) {
			return;
		}
		for (DefaultRole defaultRole : DefaultRole.values()) {
			Query query = Query.query(Criteria.where(TENANT_ID).is(tenantId).and(DEFAULT_KEY).is(defaultRole.name()));
			Update update = new Update().setOnInsert(ID, UUID.randomUUID().toString())
				.setOnInsert(NAME, defaultRole.displayName())
				.setOnInsert(NORMALIZED_NAME, NotificationRole.normalize(defaultRole.displayName()))
				.setOnInsert(IS_DEFAULT, true)
				.setOnInsert(IMMUTABLE, defaultRole.immutable())
				.setOnInsert(SEVERITIES, List.of())
				.setOnInsert(ALERT_TYPES, List.of())
				.setOnInsert(MEMBER_USER_IDS, List.of())
				.setOnInsert(DELETED, false)
				.setOnInsert(CREATED_AT, now)
				.setOnInsert(UPDATED_AT, now)
				.setOnInsert(VERSION, 0L);
			try {
				mongo.upsert(query, update, NotificationRoleDocument.class);
			}
			catch (DuplicateKeyException concurrentSeed) {
				// Another request created this default at the same moment: the unique index kept a single one.
			}
		}
	}

	@Override
	public List<NotificationRole> findActive(String tenantId) {
		return mongo.find(Query.query(active(tenantId)), NotificationRoleDocument.class).stream()
			.map(MongoNotificationRoleRepository::toDomain)
			.toList();
	}

	@Override
	public Optional<NotificationRole> findActive(String tenantId, String roleId) {
		return Optional.ofNullable(mongo.findOne(Query.query(active(tenantId).and(ID).is(roleId)),
				NotificationRoleDocument.class)).map(MongoNotificationRoleRepository::toDomain);
	}

	@Override
	public NotificationRole insert(NotificationRole role) {
		return toDomain(mongo.insert(toDocument(role)));
	}

	@Override
	public NotificationRole save(NotificationRole role) {
		return toDomain(mongo.save(toDocument(role)));
	}

	static Criteria active(String tenantId) {
		return Criteria.where(TENANT_ID).is(tenantId).and(DELETED).is(false);
	}

	static NotificationRoleDocument toDocument(NotificationRole role) {
		return new NotificationRoleDocument(role.id(), role.tenantId(), role.name(), role.normalizedName(),
				role.defaultKey() == null ? null : role.defaultKey().name(), role.isDefault(), role.immutable(),
				role.severities().stream().map(Enum::name).toList(), role.alertTypes().stream().map(Enum::name).toList(),
				role.memberUserIds(), role.deleted(), role.deletedAt(), role.createdAt(), role.updatedAt(),
				role.version());
	}

	static NotificationRole toDomain(NotificationRoleDocument document) {
		return new NotificationRole(document.id(), document.tenantId(), document.name(), document.normalizedName(),
				document.defaultKey() == null ? null : DefaultRole.valueOf(document.defaultKey()), document.isDefault(),
				document.immutable(), listOf(document.severities()).stream().map(Severity::valueOf).toList(),
				listOf(document.alertTypes()).stream().map(AlertType::valueOf).toList(),
				listOf(document.memberUserIds()), document.deleted(), document.deletedAt(), document.createdAt(),
				document.updatedAt(), document.version());
	}

	private static List<String> listOf(List<String> values) {
		return values == null ? List.of() : values;
	}

}
