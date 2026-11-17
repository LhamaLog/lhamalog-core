package br.com.lhamalog.core.usage.application;

import java.time.Duration;
import java.time.Instant;

/**
 * Single rule for the delinquency grace window (ADR_163): a DELINQUENT subscription is treated as suspended
 * from {@code delinquentSince + grace} on (the boundary instant itself is already expired). A missing
 * {@code delinquentSince} is treated conservatively as still within grace.
 */
final class DelinquencyGrace {

	private DelinquencyGrace() {
	}

	static boolean isExpired(Instant delinquentSince, Duration grace, Instant now) {
		if (delinquentSince == null) {
			return false;
		}
		return !now.isBefore(delinquentSince.plus(grace));
	}
}
