package br.com.lhamalog.core.member.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Business role of a member in its tenant (ADR_122/123). Only the purchase flow assigns {@link #DONO}; Clerk sync
 * maps {@code org:admin} to {@link #ADMIN} and {@code org:member} to {@link #MEMBRO}.
 */
public enum MemberRole {

	DONO,

	ADMIN,

	MEMBRO;

	/** Maps a Clerk organization role ({@code org:admin}, {@code admin}, ...). Empty for an unknown role. */
	public static Optional<MemberRole> fromClerk(String clerkRole) {
		if (clerkRole == null) {
			return Optional.empty();
		}
		String role = clerkRole.trim().toLowerCase(Locale.ROOT);
		if (role.startsWith("org:")) {
			role = role.substring("org:".length());
		}
		return switch (role) {
			case "admin" -> Optional.of(ADMIN);
			case "member" -> Optional.of(MEMBRO);
			default -> Optional.empty();
		};
	}

	/** Role after a sync event: a DONO is never changed by Clerk sync. */
	public MemberRole syncedTo(MemberRole fromClerk) {
		return (this == DONO) ? DONO : fromClerk;
	}

}
