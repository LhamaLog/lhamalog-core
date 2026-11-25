package br.com.lhamalog.core.member.application.port.out;

import java.util.List;
import java.util.Optional;

import br.com.lhamalog.core.member.domain.Member;

/** Output port for members. Writes are conditional so concurrent or repeated deliveries cannot lose updates. */
public interface MemberRepository {

	Optional<Member> findByTenantIdAndClerkUserId(String tenantId, String clerkUserId);

	/** All members of a user across tenants (user events are not tied to one tenant). */
	List<Member> findByClerkUserId(String clerkUserId);

	/** Inserts a new member; fails if (tenantId, clerkUserId) already exists. */
	void insert(Member member);

	/**
	 * Replaces {@code previous} with {@code updated} only if the stored member still has the versions of
	 * {@code previous}.
	 * @throws ConcurrentMemberUpdateException when another write won the race
	 */
	void update(Member previous, Member updated);

}
