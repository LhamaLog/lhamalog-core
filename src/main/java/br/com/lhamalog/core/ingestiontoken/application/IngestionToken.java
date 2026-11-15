package br.com.lhamalog.core.ingestiontoken.application;

import java.time.Instant;

/**
 * Ingestion token of a product, as stored in {@code product_tokens} (feature 005). Holds only the hash of the
 * token. The validity rule mirrors the Collector's {@code ProductTokenValidity}: active, not revoked and not
 * expired.
 */
public record IngestionToken(String id, String tenantId, String productId, String name, String tokenHash,
		Status status, Instant createdAt, String createdBy, Instant revokedAt, String revokedBy, Instant expiresAt,
		String replacedBy, Long version) {

	/** Stored status, read by the Collector. */
	public enum Status { ACTIVE, REVOKED }

	/** Status shown by the API, derived from the stored fields and the current time. */
	public enum State { ACTIVE, EXPIRING, EXPIRED, REVOKED }

	public static IngestionToken issue(String id, String tenantId, String productId, String name, String tokenHash,
			Instant createdAt, String createdBy) {
		return new IngestionToken(id, tenantId, productId, name, tokenHash, Status.ACTIVE, createdAt, createdBy, null,
				null, null, null, null);
	}

	/** Same rule as the Collector: ACTIVE, no revokedAt, and expiresAt absent or after {@code now}. */
	public boolean usableAt(Instant now) {
		return status == Status.ACTIVE && revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
	}

	public State stateAt(Instant now) {
		if (status == Status.REVOKED) return State.REVOKED;
		if (expiresAt == null) return State.ACTIVE;
		return expiresAt.isAfter(now) ? State.EXPIRING : State.EXPIRED;
	}

	public IngestionToken revoke(Instant at, String by) {
		return new IngestionToken(id, tenantId, productId, name, tokenHash, Status.REVOKED, createdAt, createdBy, at, by,
				expiresAt, replacedBy, version);
	}

	/** Marks this token to expire at the end of a rotation, pointing to the token that replaces it. */
	public IngestionToken rotateOut(Instant expiresAt, String replacedBy) {
		return new IngestionToken(id, tenantId, productId, name, tokenHash, status, createdAt, createdBy, revokedAt,
				revokedBy, expiresAt, replacedBy, version);
	}
}
