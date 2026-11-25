package br.com.lhamalog.core.ingestiontoken.application;

import java.time.Instant;

/**
 * Immutable record of an operation on an ingestion token (feature 010). Never carries the token value or hash.
 * {@code relatedTokenId} and {@code transitionEndsAt} are filled only for {@link Action#ROTATED}.
 */
public record TokenAuditEntry(String id, String tenantId, String productId, String tokenId, Action action,
		String actorUserId, Instant at, String relatedTokenId, Instant transitionEndsAt) {

	public enum Action { CREATED, REVOKED, ROTATED }
}
