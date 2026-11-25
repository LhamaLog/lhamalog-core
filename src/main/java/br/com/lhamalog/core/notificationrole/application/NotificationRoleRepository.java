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

	/**
	 * Adds the person atomically ({@code $addToSet}, no version sent by the client). Does nothing when the person is
	 * already there.
	 * @return false when the role is full ({@code maxMembers}) and the person is not in it yet
	 */
	boolean addMember(String tenantId, String roleId, String userId, int maxMembers, Instant now);

	/** Removes the person atomically ({@code $pull}); nothing happens if the person is not there. */
	void removeMember(String tenantId, String roleId, String userId, Instant now);

	/** Active roles that list the person explicitly. */
	List<NotificationRole> findActiveByMember(String tenantId, String userId);

}
