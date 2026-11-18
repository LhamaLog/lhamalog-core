package br.com.lhamalog.core.member.domain;

import java.util.Objects;

/**
 * State of a Clerk organization membership carried by a webhook.
 *
 * @param clerkOrgId organization id
 * @param clerkUserId user id
 * @param clerkMembershipId membership id ({@code orgmem_...}); may be {@code null}
 * @param clerkRole raw Clerk role ({@code org:admin}, {@code org:member}, ...)
 * @param name display name from the membership's public user data; may be {@code null}
 * @param email email from the public user data identifier; may be {@code null}
 * @param version {@code updated_at} of the membership (ms), or the event timestamp for removals
 */
public record MembershipSnapshot(String clerkOrgId, String clerkUserId, String clerkMembershipId, String clerkRole,
		String name, String email, long version) {

	public MembershipSnapshot {
		Objects.requireNonNull(clerkOrgId, "clerkOrgId");
		Objects.requireNonNull(clerkUserId, "clerkUserId");
	}

}
