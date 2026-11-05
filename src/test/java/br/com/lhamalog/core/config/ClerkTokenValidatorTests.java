package br.com.lhamalog.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class ClerkTokenValidatorTests {
	private static RSAKey key;
	private static NimbusJwtDecoder decoder;

	@BeforeAll
	static void setup() throws JOSEException {
		key = new RSAKeyGenerator(2048).generate();
		decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer("https://clerk.example"),
				new ClerkTokenValidator(Set.of("http://localhost:5173"))));
	}

	@Test
	void acceptsSignedActiveSession() throws Exception {
		String token = token(key, claims -> {});
		assertDoesNotThrow(() -> decoder.decode(token));
	}

	@Test
	void rejectsInvalidSignature() throws Exception {
		String token = token(new RSAKeyGenerator(2048).generate(), claims -> {});
		assertThrows(JwtException.class, () -> decoder.decode(token));
	}

	@Test
	void rejectsExpiredAndFutureSessions() throws Exception {
		rejects(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120))));
		rejects(claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(120))));
	}

	@Test
	void rejectsWrongIssuerOriginAndPendingSession() throws Exception {
		rejects(claims -> claims.issuer("https://other.example"));
		rejects(claims -> claims.claim("azp", "https://other.example"));
		rejects(claims -> claims.claim("sts", "pending"));
	}

	@Test
	void rejectsMissingRequiredClaims() throws Exception {
		rejects(claims -> claims.subject(null));
		rejects(claims -> claims.expirationTime(null));
		rejects(claims -> claims.notBeforeTime(null));
		rejects(claims -> claims.claim("azp", null));
	}

	private void rejects(Consumer<JWTClaimsSet.Builder> change) throws Exception {
		String token = token(key, change);
		assertThrows(JwtException.class, () -> decoder.decode(token));
	}

	private static String token(RSAKey signingKey, Consumer<JWTClaimsSet.Builder> change) throws JOSEException {
		Instant now = Instant.now();
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("user_test")
				.issuer("https://clerk.example").issueTime(Date.from(now))
				.notBeforeTime(Date.from(now.minusSeconds(1)))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.claim("azp", "http://localhost:5173").claim("sts", "active");
		change.accept(claims);
		SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
		signed.sign(new RSASSASigner(signingKey));
		return signed.serialize();
	}
}
