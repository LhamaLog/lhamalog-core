package br.com.lhamalog.core.member.domain;

import java.util.Objects;

/**
 * User data kept on every member of the user (ADR_159).
 *
 * @param clerkUserId user id
 * @param name display name; may be {@code null}
 * @param email primary email; may be {@code null}
 * @param version {@code updated_at} of the user (ms)
 */
public record UserProfile(String clerkUserId, String name, String email, long version) {

	public UserProfile {
		Objects.requireNonNull(clerkUserId, "clerkUserId");
	}

}
