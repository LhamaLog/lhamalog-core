package br.com.lhamalog.core.access.application;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of who holds which role in a company (feature 011, clarify C1 of 18/11). Today two collections hold
 * this: {@code member} (LLT-163, roles DONO/ADMIN/MEMBRO synced from Clerk) and {@code CompanyMember} (LLT-168, the
 * admin flag seen on each request). Only the adapter of this port reads them, so unifying both (decision D1) changes
 * one class. Nothing here writes: DONO is assigned by the purchase flow (ADR_121).
 */
public interface MemberRoleDirectory {

	/** Role from an active {@code member} (not removed, not deleted): DONO, ADMIN or MEMBRO. Empty without one. */
	Optional<AccessRole> memberRole(String tenantId, String userId);

	/** Whether the LLT-168 {@code CompanyMember} of the person was last seen with an admin token. */
	boolean seenAsAdministrator(String tenantId, String userId);

	/** Clerk user ids of the active DONO members of the company, in ascending order. Normally zero or one. */
	List<String> activeOwners(String tenantId);

}
