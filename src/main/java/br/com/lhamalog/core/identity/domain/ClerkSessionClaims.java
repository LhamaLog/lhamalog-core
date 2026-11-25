package br.com.lhamalog.core.identity.domain;

import java.util.Objects;

/**
 * Claims of an already validated Clerk session token.
 *
 * @param subject Clerk user id ({@code sub})
 * @param sessionId Clerk session id ({@code sid})
 * @param organizationId active organization id ({@code o.id}); {@code null} when there is no active organization
 * @param organizationRole raw role ({@code o.rol}); may be {@code null}
 */
public record ClerkSessionClaims(String subject, String sessionId, String organizationId, String organizationRole) {

	public ClerkSessionClaims {
		Objects.requireNonNull(subject, "subject");
		Objects.requireNonNull(sessionId, "sessionId");
	}

	public boolean hasActiveOrganization() {
		return organizationId != null && !organizationId.isBlank();
	}

}
