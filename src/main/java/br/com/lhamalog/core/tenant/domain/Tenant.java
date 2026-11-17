package br.com.lhamalog.core.tenant.domain;

import java.util.Objects;

/**
 * Customer company, mirror of a Clerk Organization (ADR_121/122). {@code tenantId} is the isolation key of the Core.
 */
public record Tenant(String tenantId, String clerkOrgId, String name, TenantStatus status) {

	public Tenant {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(clerkOrgId, "clerkOrgId");
		Objects.requireNonNull(status, "status");
	}

	public boolean isActive() {
		return status == TenantStatus.ACTIVE;
	}

}
