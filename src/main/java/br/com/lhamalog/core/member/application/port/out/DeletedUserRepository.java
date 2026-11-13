package br.com.lhamalog.core.member.application.port.out;

import java.time.Instant;

/** Permanent marker of users deleted in Clerk, so no later event recreates them (ADR_022). */
public interface DeletedUserRepository {

	boolean isDeleted(String clerkUserId);

	/** Idempotent: keeps the first deletion time. */
	void markDeleted(String clerkUserId, Instant deletedAt);

}
