package br.com.lhamalog.core.access.application;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.shared.CompanySession;

/**
 * Resolves access roles (feature 011, US1). The only place that knows how the owner is found, so a future change of
 * source stays here and in {@link MemberRoleDirectory}. Never writes: DONO comes from the purchase flow (ADR_121).
 */
@Service
public class AccessService {

	private static final Logger log = LoggerFactory.getLogger(AccessService.class);

	private final MemberRoleDirectory directory;

	public AccessService(MemberRoleDirectory directory) {
		this.directory = directory;
	}

	/**
	 * Role of the person of the session. The token decides the power: an owner whose token says {@code member} (demoted
	 * outside the system) is a MEMBER, with a warning (FR-003).
	 */
	public AccessRole resolve(CompanySession session) {
		boolean owner = isOwner(session.tenantId(), session.userId());
		if (session.administrator()) {
			return owner ? AccessRole.OWNER : AccessRole.ADMIN;
		}
		if (owner) {
			log.warn("event=access.owner.not_admin | Owner arrived with a member token; treated as MEMBER | tenantId={} userHash={}",
					session.tenantId(), UserHash.of(session.userId()));
		}
		return AccessRole.MEMBER;
	}

	/** Resolves the role of the session and refuses with 403 {@code not_admin} when it lacks the permission. */
	public AccessRole require(CompanySession session, Permission permission) {
		AccessRole role = resolve(session);
		AccessPolicy.require(role, permission);
		return role;
	}

	/**
	 * Role of another person, whose token is not at hand: the active {@code member} first (LLT-163), then the admin flag
	 * of the {@code CompanyMember} (LLT-168), otherwise MEMBER.
	 */
	public AccessRole roleOf(String tenantId, String userId) {
		return directory.memberRole(tenantId, userId)
			.orElseGet(() -> directory.seenAsAdministrator(tenantId, userId) ? AccessRole.ADMIN : AccessRole.MEMBER);
	}

	/** Whether the person has full product access: owner or administrator. */
	public boolean hasFullAccess(String tenantId, String userId) {
		return roleOf(tenantId, userId) != AccessRole.MEMBER;
	}

	public boolean isOwner(String tenantId, String userId) {
		return directory.memberRole(tenantId, userId).filter((role) -> role == AccessRole.OWNER).isPresent();
	}

	/** Owner of the company. More than one active DONO is invalid data: the smallest user id wins, with a warning. */
	public Optional<String> ownerOf(String tenantId) {
		List<String> owners = directory.activeOwners(tenantId);
		if (owners.size() > 1) {
			log.warn("event=access.owner.duplicated | More than one active DONO member; using the smallest user id | tenantId={} owners={}",
					tenantId, owners.size());
		}
		return owners.stream().findFirst();
	}

}
