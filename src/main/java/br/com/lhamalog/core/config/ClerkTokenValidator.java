package br.com.lhamalog.core.config;

import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class ClerkTokenValidator implements OAuth2TokenValidator<Jwt> {

	private final Set<String> authorizedParties;

	public ClerkTokenValidator(Set<String> authorizedParties) {
		this.authorizedParties = Set.copyOf(authorizedParties);
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (token.getSubject() == null || token.getSubject().isBlank()
				|| token.getExpiresAt() == null || token.getNotBefore() == null
				|| token.getClaimAsString("azp") == null
				|| !authorizedParties.contains(token.getClaimAsString("azp"))
				|| "pending".equals(token.getClaimAsString("sts"))) {
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_token", "Invalid Clerk session", null));
		}
		return OAuth2TokenValidatorResult.success();
	}
}
