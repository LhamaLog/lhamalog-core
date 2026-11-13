package br.com.lhamalog.core.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Clerk integration settings. Values come from environment variables (see application*.properties);
 * nothing secret has a default.
 *
 * @param issuer Frontend API URL of the Clerk instance; must match the {@code iss} claim
 * @param jwksUri optional override of the JWKS endpoint; defaults to {@code issuer + /.well-known/jwks.json}
 * @param authorizedParties allowed {@code azp} values (front-end origins), also used for CORS
 * @param clockSkew tolerance applied to {@code exp} and {@code nbf}
 * @param secretKey Clerk Backend API secret key; when blank, session revocation is skipped
 * @param apiUrl Clerk Backend API base URL
 * @param connectTimeout connect timeout for Backend API calls
 * @param readTimeout read timeout for Backend API calls
 */
@Validated
@ConfigurationProperties("clerk")
public record ClerkProperties(
		@NotBlank String issuer,
		String jwksUri,
		@DefaultValue List<String> authorizedParties,
		@DefaultValue("5s") Duration clockSkew,
		String secretKey,
		@DefaultValue("https://api.clerk.com") String apiUrl,
		@DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("3s") Duration readTimeout) {

	private static final String JWKS_PATH = "/.well-known/jwks.json";

	public ClerkProperties {
		issuer = stripTrailingSlash(issuer);
		authorizedParties = authorizedParties.stream().map(String::trim).filter((origin) -> !origin.isEmpty()).toList();
		apiUrl = stripTrailingSlash(apiUrl);
	}

	/** JWKS endpoint actually used to fetch the signing keys. */
	public String resolvedJwksUri() {
		return (jwksUri == null || jwksUri.isBlank()) ? issuer + JWKS_PATH : jwksUri.trim();
	}

	public boolean hasSecretKey() {
		return secretKey != null && !secretKey.isBlank();
	}

	private static String stripTrailingSlash(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}

	@Override
	public String toString() {
		// Never print the secret key.
		return "ClerkProperties[issuer=" + issuer + ", jwksUri=" + resolvedJwksUri() + ", authorizedParties="
				+ authorizedParties + ", clockSkew=" + clockSkew + ", secretKey=" + (hasSecretKey() ? "***" : "<unset>")
				+ ", apiUrl=" + apiUrl + "]";
	}

}
