package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;

/**
 * User actions on an occurrence (spec 014): each one changes only its fields, gives the occurrence a new revision and
 * applies only if the revision is still the one the user saw (research R2). False means nothing was changed.
 */
public interface OccurrenceCommandRepository {

	boolean changeStatus(String tenantId, String id, String expectedRevision, String status, String from,
			Instant changedAt, String userId, String newRevision);

	boolean archive(String tenantId, String id, String expectedRevision, Instant archivedAt, String userId,
			String newRevision);

	boolean unarchive(String tenantId, String id, String expectedRevision, String newRevision);

}
