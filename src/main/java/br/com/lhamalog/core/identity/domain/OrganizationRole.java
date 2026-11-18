package br.com.lhamalog.core.identity.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Role of the user in the active Clerk Organization ({@code o.rol} claim). Business roles DONO/ADMIN/MEMBRO live in
 * the Membro collection (ADR_122/123).
 */
public enum OrganizationRole {

	ADMIN,

	MEMBER;

	private static final String CLERK_PREFIX = "org:";

	/**
	 * Normalizes a Clerk role. Session token v2 sends it without the {@code org:} prefix ({@code admin}); the prefixed
	 * form ({@code org:admin}) is accepted as well.
	 */
	public static Optional<OrganizationRole> fromClerk(String rawRole) {
		if (rawRole == null) {
			return Optional.empty();
		}
		String role = rawRole.trim().toLowerCase(Locale.ROOT);
		if (role.startsWith(CLERK_PREFIX)) {
			role = role.substring(CLERK_PREFIX.length());
		}
		return switch (role) {
			case "admin" -> Optional.of(ADMIN);
			case "member" -> Optional.of(MEMBER);
			default -> Optional.empty();
		};
	}

}
