package br.com.lhamalog.core.ingestiontoken.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests, no database. */
class TokenSecretsTest {
	private final TokenSecrets secrets = new TokenSecrets();

	@Test
	void generatesPrefixedUrlSafeTokensWith256BitsOfRandomness() {
		String token = secrets.generate();
		assertTrue(token.matches("^llt_[A-Za-z0-9_-]{43}$"), token.length() + " characters");
		assertEquals(47, token.length());
	}

	@Test
	void everyTokenIsDifferent() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) assertTrue(seen.add(secrets.generate()));
	}

	@Test
	void hashIsLowercaseHexSha256OfUtf8Bytes() {
		// Known vector: SHA-256("abc").
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenSecrets.sha256Hex("abc"));
		String hash = TokenSecrets.sha256Hex(secrets.generate());
		assertTrue(hash.matches("^[0-9a-f]{64}$"));
	}
}
