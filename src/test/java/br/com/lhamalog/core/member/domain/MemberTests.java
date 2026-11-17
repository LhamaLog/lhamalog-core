package br.com.lhamalog.core.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberTests {

	private static final Instant AT = Instant.parse("2026-11-06T12:00:00Z");

	@ParameterizedTest
	@CsvSource({ "org:admin, ADMIN", "admin, ADMIN", "org:member, MEMBRO", "member, MEMBRO" })
	void mapsClerkRoles(String clerkRole, MemberRole expected) {
		assertThat(MemberRole.fromClerk(clerkRole)).contains(expected);
	}

	@Test
	void unknownRoleBecomesMembro() {
		assertThat(MemberRole.fromClerk("org:billing")).isEmpty();
		assertThat(Member.joined("m1", "t1", membership("org:billing", 10)).role()).isEqualTo(MemberRole.MEMBRO);
	}

	@Test
	void ownerIsNeverDemotedBySync() {
		Member owner = member(MemberRole.DONO, 10);

		assertThat(owner.applyMembership(membership("org:member", 20))).hasValueSatisfying(
				(updated) -> assertThat(updated.role()).isEqualTo(MemberRole.DONO));
		assertThat(owner.applyMembership(membership("org:admin", 30))).hasValueSatisfying(
				(updated) -> assertThat(updated.role()).isEqualTo(MemberRole.DONO));
	}

	@Test
	void adminCanBecomeMembro() {
		assertThat(member(MemberRole.ADMIN, 10).applyMembership(membership("org:member", 20)))
			.hasValueSatisfying((updated) -> assertThat(updated.role()).isEqualTo(MemberRole.MEMBRO));
	}

	@Test
	void staleOrRepeatedMembershipIsIgnored() {
		Member member = member(MemberRole.ADMIN, 10);

		assertThat(member.applyMembership(membership("org:member", 10))).isEmpty();
		assertThat(member.applyMembership(membership("org:member", 5))).isEmpty();
	}

	@Test
	void removalAndNewerMembershipReactivates() {
		Member removed = member(MemberRole.ADMIN, 10).applyRemoval(20, AT).orElseThrow();
		assertThat(removed.isRemoved()).isTrue();

		assertThat(removed.applyMembership(membership("org:admin", 15))).isEmpty();
		assertThat(removed.applyMembership(membership("org:admin", 25)))
			.hasValueSatisfying((updated) -> assertThat(updated.isRemoved()).isFalse());
	}

	@Test
	void profileFollowsUserVersion() {
		Member updated = member(MemberRole.MEMBRO, 10).applyProfile(new UserProfile("u1", "Ana", "ana@x.test", 100))
			.orElseThrow();
		assertThat(updated.name()).isEqualTo("Ana");
		assertThat(updated.applyProfile(new UserProfile("u1", "Old", "old@x.test", 50))).isEmpty();
		// Once a user event was applied, membership events no longer overwrite the profile.
		assertThat(updated.applyMembership(membership("org:member", 20)).orElseThrow().name()).isEqualTo("Ana");
	}

	@Test
	void deletedMemberNeverChanges() {
		Member deleted = member(MemberRole.ADMIN, 10).applyUserDeletion(AT).orElseThrow();

		assertThat(deleted.isDeleted()).isTrue();
		assertThat(deleted.applyMembership(membership("org:admin", 99))).isEmpty();
		assertThat(deleted.applyRemoval(99, AT)).isEmpty();
		assertThat(deleted.applyProfile(new UserProfile("u1", "X", null, 99))).isEmpty();
		assertThat(deleted.applyUserDeletion(AT)).isEmpty();
	}

	private static Member member(MemberRole role, long membershipVersion) {
		return new Member("m1", "t1", "u1", "orgmem_1", role, "Name", "n@x.test", membershipVersion, 0, null, null);
	}

	private static MembershipSnapshot membership(String role, long version) {
		return new MembershipSnapshot("org_1", "u1", "orgmem_1", role, "Snapshot Name", "s@x.test", version);
	}

}
