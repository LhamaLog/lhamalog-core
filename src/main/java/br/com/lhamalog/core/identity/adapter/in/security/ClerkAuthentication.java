package br.com.lhamalog.core.identity.adapter.in.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.identity.domain.ClerkSessionClaims;

/**
 * Authentication built from a valid Clerk session token. It is always authenticated; whether access is granted is
 * carried by {@link #decision()} and enforced by {@link TenantAccessAuthorizationManager}.
 */
public final class ClerkAuthentication extends AbstractAuthenticationToken {

	private final transient Jwt token;

	private final ClerkSessionClaims claims;

	private final AccessDecision decision;

	public ClerkAuthentication(Jwt token, ClerkSessionClaims claims, AccessDecision decision) {
		super(List.of());
		this.token = token;
		this.claims = claims;
		this.decision = decision;
		setAuthenticated(true);
	}

	public ClerkSessionClaims claims() {
		return claims;
	}

	public AccessDecision decision() {
		return decision;
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public Object getPrincipal() {
		return claims.subject();
	}

	@Override
	public String getName() {
		return claims.subject();
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

}
