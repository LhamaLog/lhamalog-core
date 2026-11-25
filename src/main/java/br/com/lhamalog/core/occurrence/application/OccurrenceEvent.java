package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;

/**
 * Notice that an occurrence is new for the screen (spec 015): created by the Finder or reopened by a new finding.
 * Carries no title, signature or log content (FR-007); {@code tenantId} only routes it.
 */
public record OccurrenceEvent(String tenantId, String productId, String occurrenceId, String occurrenceType,
		Change change, Instant at) {

	public enum Change {
		CREATED, REOPENED
	}

}
