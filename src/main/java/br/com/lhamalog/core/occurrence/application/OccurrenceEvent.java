package br.com.lhamalog.core.occurrence.application;

import java.time.Instant;

/**
 * Notice that an occurrence changed for the screen (spec 015 and spec 016). One notice per occurrence per delivery,
 * the strongest change: created, then reopened, then became recurring. Carries no title, signature or log content;
 * {@code tenantId} only routes it. {@code gravidade} is the class, the same value stored on the occurrence.
 */
public record OccurrenceEvent(String tenantId, String productId, String occurrenceId, String occurrenceType,
		String gravidade, Change change, Instant at) {

	public enum Change {
		CREATED, REOPENED, RECURRENT
	}

}
