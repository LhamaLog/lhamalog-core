package br.com.lhamalog.core.identity.adapter.in.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires an active Clerk session: {@code sts = active} plus the {@code sub} and {@code sid} claims. A
 * {@code pending} session (e.g. MFA not set up yet) counts as signed out (ADR_032), and so does a missing claim.
 */
public final class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

	static final String STS = "sts";

	static final String ACTIVE = "active";

	private static final OAuth2Error INVALID = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid token", null);

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		boolean valid = ACTIVE.equals(token.getClaimAsString(STS)) && hasText(token.getSubject())
				&& hasText(token.getClaimAsString(ClerkAuthenticationConverter.SID));
		return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
