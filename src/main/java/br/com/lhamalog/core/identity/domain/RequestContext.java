package br.com.lhamalog.core.identity.domain;

import java.util.Objects;

/** Identity of the current authenticated request, free of Spring and Clerk types. */
public record RequestContext(String clerkUserId, String sessionId, String clerkOrgId, OrganizationRole role,
		String tenantId) {

	public RequestContext {
		Objects.requireNonNull(clerkUserId, "clerkUserId");
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(clerkOrgId, "clerkOrgId");
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(tenantId, "tenantId");
	}

}
