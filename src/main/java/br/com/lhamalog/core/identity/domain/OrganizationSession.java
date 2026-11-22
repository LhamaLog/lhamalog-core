package br.com.lhamalog.core.identity.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Identity of a request on the purchase routes (spec 010): a valid session with an active organization and a known
 * role, whose organization may not have a tenant yet.
 *
 * @param tenantId internal tenant id; {@code null} while the organization has no tenant
 */
public record OrganizationSession(String clerkUserId, String clerkOrgId, OrganizationRole role, String tenantId) {

	public OrganizationSession {
		Objects.requireNonNull(clerkUserId, "clerkUserId");
		Objects.requireNonNull(clerkOrgId, "clerkOrgId");
		Objects.requireNonNull(role, "role");
	}

	public Optional<String> tenant() {
		return Optional.ofNullable(tenantId);
	}

	public boolean administrator() {
		return role == OrganizationRole.ADMIN;
	}

}
