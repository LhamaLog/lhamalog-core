package br.com.lhamalog.core.identity.adapter.in.security;

import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.lhamalog.core.identity.application.AccessResolver;
import br.com.lhamalog.core.identity.domain.ClerkSessionClaims;

/** Turns a validated Clerk JWT into a {@link ClerkAuthentication}, resolving tenant access on the way. */
public final class ClerkAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	static final String SID = "sid";

	static final String ORGANIZATION = "o";

	static final String ORGANIZATION_ID = "id";

	static final String ORGANIZATION_ROLE = "rol";

	private final AccessResolver accessResolver;

	public ClerkAuthenticationConverter(AccessResolver accessResolver) {
		this.accessResolver = accessResolver;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		ClerkSessionClaims claims = toClaims(jwt);
		return new ClerkAuthentication(jwt, claims, accessResolver.resolve(claims));
	}

	static ClerkSessionClaims toClaims(Jwt jwt) {
		Map<String, Object> organization = jwt.getClaimAsMap(ORGANIZATION);
		String organizationId = (organization != null) ? asString(organization.get(ORGANIZATION_ID)) : null;
		String organizationRole = (organization != null) ? asString(organization.get(ORGANIZATION_ROLE)) : null;
		return new ClerkSessionClaims(jwt.getSubject(), jwt.getClaimAsString(SID), organizationId, organizationRole);
	}

	private static String asString(Object value) {
		return (value instanceof String text) ? text : null;
	}

}
