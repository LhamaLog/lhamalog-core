package br.com.lhamalog.core.member;

import java.time.Duration;
import java.time.Instant;

public final class MemberObservation {
	public static final Duration PRODUCTION_INTERVAL = Duration.ofMinutes(5);

	private MemberObservation() {}

	public static boolean shouldWrite(CompanyMember current, boolean administrator, Instant now, Duration interval) {
		if (current == null || current.lastAccessAt() == null) {
			return true;
		}
		if (current.administrator() != administrator) {
			return true;
		}
		return !current.lastAccessAt().plus(interval).isAfter(now);
	}
}
