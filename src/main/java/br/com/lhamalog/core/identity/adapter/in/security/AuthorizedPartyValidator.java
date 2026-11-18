package br.com.lhamalog.core.identity.adapter.in.security;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Requires the {@code azp} claim to be one of the allowed front-end origins (CSRF protection, ADR_022). */
public final class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

	static final String AZP = "azp";

	private static final OAuth2Error INVALID = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid token", null);

	private final Set<String> authorizedParties;

	public AuthorizedPartyValidator(Collection<String> authorizedParties) {
		this.authorizedParties = Set.copyOf(authorizedParties);
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		String azp = token.getClaimAsString(AZP);
		return (azp != null && authorizedParties.contains(azp)) ? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(INVALID);
	}

}
