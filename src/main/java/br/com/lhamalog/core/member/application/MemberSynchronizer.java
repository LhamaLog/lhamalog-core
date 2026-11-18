package br.com.lhamalog.core.member.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.lhamalog.core.member.application.port.out.DeletedUserRepository;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.member.domain.Member;
import br.com.lhamalog.core.member.domain.MemberRole;
import br.com.lhamalog.core.member.domain.MembershipSnapshot;
import br.com.lhamalog.core.member.domain.UserProfile;
import br.com.lhamalog.core.tenant.application.port.out.TenantRepository;
import br.com.lhamalog.core.tenant.domain.Tenant;

/**
 * Keeps members in sync with Clerk users and organization memberships (LLT-164). Never creates tenants (ADR_121) and
 * never recreates a deleted user (ADR_022).
 */
public class MemberSynchronizer {

	private static final Logger log = LoggerFactory.getLogger(MemberSynchronizer.class);

	private final TenantRepository tenants;

	private final MemberRepository members;

	private final DeletedUserRepository deletedUsers;

	private final Supplier<String> idGenerator;

	public MemberSynchronizer(TenantRepository tenants, MemberRepository members, DeletedUserRepository deletedUsers) {
		this(tenants, members, deletedUsers, () -> UUID.randomUUID().toString());
	}

	MemberSynchronizer(TenantRepository tenants, MemberRepository members, DeletedUserRepository deletedUsers,
			Supplier<String> idGenerator) {
		this.tenants = tenants;
		this.members = members;
		this.deletedUsers = deletedUsers;
		this.idGenerator = idGenerator;
	}

	/**
	 * Membership created or updated.
	 * @return the tenant of the membership, or empty when the organization has no tenant
	 */
	public Optional<String> syncMembership(MembershipSnapshot membership) {
		Optional<Tenant> tenant = tenantFor(membership);
		if (tenant.isEmpty() || isDeletedUser(membership.clerkUserId())) {
			return tenant.map(Tenant::tenantId);
		}
		warnOnUnknownRole(membership);
		String tenantId = tenant.get().tenantId();
		Optional<Member> current = members.findByTenantIdAndClerkUserId(tenantId, membership.clerkUserId());
		if (current.isEmpty()) {
			members.insert(Member.joined(idGenerator.get(), tenantId, membership));
		}
		else {
			current.get().applyMembership(membership).ifPresent((updated) -> members.update(current.get(), updated));
		}
		return Optional.of(tenantId);
	}

	/**
	 * Membership removed from the organization.
	 * @return the tenant of the membership, or empty when the organization has no tenant
	 */
	public Optional<String> removeMembership(MembershipSnapshot membership, Instant removedAt) {
		Optional<Tenant> tenant = tenantFor(membership);
		if (tenant.isEmpty() || isDeletedUser(membership.clerkUserId())) {
			return tenant.map(Tenant::tenantId);
		}
		String tenantId = tenant.get().tenantId();
		Optional<Member> current = members.findByTenantIdAndClerkUserId(tenantId, membership.clerkUserId());
		if (current.isEmpty()) {
			members.insert(Member.removedBeforeSeen(idGenerator.get(), tenantId, membership, removedAt));
		}
		else {
			if (current.get().role() == MemberRole.DONO) {
				log.warn("Owner membership removed in Clerk: tenantId={} clerkUserId={}", tenantId,
						membership.clerkUserId());
			}
			current.get()
				.applyRemoval(membership.version(), removedAt)
				.ifPresent((updated) -> members.update(current.get(), updated));
		}
		return Optional.of(tenantId);
	}

	/** User created or updated: refreshes name and email on all the user's members. */
	public void syncUserProfile(UserProfile profile) {
		if (isDeletedUser(profile.clerkUserId())) {
			return;
		}
		for (Member member : members.findByClerkUserId(profile.clerkUserId())) {
			member.applyProfile(profile).ifPresent((updated) -> members.update(member, updated));
		}
	}

	/** User deleted: permanent marker plus soft delete of all the user's members. */
	public void deleteUser(String clerkUserId, Instant deletedAt) {
		deletedUsers.markDeleted(clerkUserId, deletedAt);
		for (Member member : members.findByClerkUserId(clerkUserId)) {
			member.applyUserDeletion(deletedAt).ifPresent((updated) -> members.update(member, updated));
		}
	}

	private Optional<Tenant> tenantFor(MembershipSnapshot membership) {
		Optional<Tenant> tenant = tenants.findByClerkOrgId(membership.clerkOrgId());
		if (tenant.isEmpty()) {
			log.info("Ignoring membership event of organization without tenant: clerkOrgId={}",
					membership.clerkOrgId());
		}
		return tenant;
	}

	private boolean isDeletedUser(String clerkUserId) {
		boolean deleted = deletedUsers.isDeleted(clerkUserId);
		if (deleted) {
			log.info("Ignoring event of deleted user: clerkUserId={}", clerkUserId);
		}
		return deleted;
	}

	private static void warnOnUnknownRole(MembershipSnapshot membership) {
		if (MemberRole.fromClerk(membership.clerkRole()).isEmpty()) {
			log.warn("Unknown Clerk role for clerkUserId={}; using MEMBRO", membership.clerkUserId());
		}
	}

}
