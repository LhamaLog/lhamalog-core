package br.com.lhamalog.core.member.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Marker of a user deleted in Clerk. {@code tenantId} is always {@code null}: a user is not tied to a single tenant
 * (explicit exception to "every document carries tenantId", spec FR-011).
 */
@Document(DeletedUserDocument.COLLECTION)
public record DeletedUserDocument(@Id String clerkUserId, String tenantId, Instant deletedAt) {

	public static final String COLLECTION = "deleted_clerk_user";

}
