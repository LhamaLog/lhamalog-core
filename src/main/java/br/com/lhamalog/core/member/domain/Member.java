package br.com.lhamalog.core.member.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A person in a tenant (Membro, ADR_122/159). Clerk sync events are applied through the {@code apply*} methods, which
 * return the new state or empty when the event must be ignored (stale version, deleted user, nothing to change).
 *
 * @param membershipVersion version (ms) of the last membership event applied
 * @param userVersion version (ms) of the last user event applied; 0 when none
 * @param removedAt set when the membership was removed from the organization; cleared by a newer membership
 * @param deletedAt set when the user was deleted in Clerk; final
 */
public record Member(String memberId, String tenantId, String clerkUserId, String clerkMembershipId, MemberRole role,
		String name, String email, long membershipVersion, long userVersion, Instant removedAt, Instant deletedAt) {

	public Member {
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(clerkUserId, "clerkUserId");
		Objects.requireNonNull(role, "role");
	}

	/** New active member from a membership event. */
	public static Member joined(String memberId, String tenantId, MembershipSnapshot membership) {
		return new Member(memberId, tenantId, membership.clerkUserId(), membership.clerkMembershipId(),
				roleFrom(membership), membership.name(), membership.email(), membership.version(), 0, null, null);
	}

	/**
	 * Member first seen through a removal (out-of-order delivery): stored as removed so a late, older creation does not
	 * activate it.
	 */
	public static Member removedBeforeSeen(String memberId, String tenantId, MembershipSnapshot membership,
			Instant removedAt) {
		return new Member(memberId, tenantId, membership.clerkUserId(), membership.clerkMembershipId(),
				roleFrom(membership), membership.name(), membership.email(), membership.version(), 0, removedAt, null);
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isRemoved() {
		return removedAt != null;
	}

	/** Membership created or updated: role (DONO kept), reactivation, profile while no user event was applied. */
	public Optional<Member> applyMembership(MembershipSnapshot membership) {
		if (isDeleted() || membership.version() <= membershipVersion) {
			return Optional.empty();
		}
		boolean refreshProfile = userVersion == 0;
		return Optional.of(new Member(memberId, tenantId, clerkUserId,
				(membership.clerkMembershipId() != null) ? membership.clerkMembershipId() : clerkMembershipId,
				role.syncedTo(roleFrom(membership)), refreshProfile ? membership.name() : name,
				refreshProfile ? membership.email() : email, membership.version(), userVersion, null, null));
	}

	/** Membership removed from the organization. */
	public Optional<Member> applyRemoval(long version, Instant removedAt) {
		if (isDeleted() || version <= membershipVersion) {
			return Optional.empty();
		}
		return Optional.of(new Member(memberId, tenantId, clerkUserId, clerkMembershipId, role, name, email, version,
				userVersion, removedAt, null));
	}

	/** User data changed in Clerk. */
	public Optional<Member> applyProfile(UserProfile profile) {
		if (isDeleted() || profile.version() <= userVersion) {
			return Optional.empty();
		}
		return Optional.of(new Member(memberId, tenantId, clerkUserId, clerkMembershipId, role, profile.name(),
				profile.email(), membershipVersion, profile.version(), removedAt, null));
	}

	/** User deleted in Clerk (soft delete, final). */
	public Optional<Member> applyUserDeletion(Instant deletedAt) {
		if (isDeleted()) {
			return Optional.empty();
		}
		return Optional.of(new Member(memberId, tenantId, clerkUserId, clerkMembershipId, role, name, email,
				membershipVersion, userVersion, removedAt, deletedAt));
	}

	/** Unknown Clerk roles fall back to the least privileged role. */
	private static MemberRole roleFrom(MembershipSnapshot membership) {
		return MemberRole.fromClerk(membership.clerkRole()).orElse(MemberRole.MEMBRO);
	}

}
