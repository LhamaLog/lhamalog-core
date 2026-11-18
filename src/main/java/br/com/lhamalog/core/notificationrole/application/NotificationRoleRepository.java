package br.com.lhamalog.core.notificationrole.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Output port for notification roles. Only roles not deleted are visible through the find methods. */
public interface NotificationRoleRepository {

	/** Creates the five default roles that are missing; safe to call concurrently and repeatedly. */
	void ensureDefaults(String tenantId, Instant now);

	List<NotificationRole> findActive(String tenantId);

	Optional<NotificationRole> findActive(String tenantId, String roleId);

	/** @throws org.springframework.dao.DuplicateKeyException when an active role of the company has the same name */
	NotificationRole insert(NotificationRole role);

	/**
	 * Saves the role if the stored version still equals {@code role.version()}, and returns it with the new version.
	 * @throws org.springframework.dao.OptimisticLockingFailureException when someone changed it meanwhile
	 * @throws org.springframework.dao.DuplicateKeyException when an active role of the company has the same name
	 */
	NotificationRole save(NotificationRole role);

}
