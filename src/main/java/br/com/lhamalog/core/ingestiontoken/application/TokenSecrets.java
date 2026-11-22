package br.com.lhamalog.core.ingestiontoken.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generates ingestion tokens and computes the hash the Collector looks up. The token value is never stored or
 * logged; only {@link #sha256Hex(String)} of it goes to the database.
 */
@Component
public class TokenSecrets {
	public static final String PREFIX = "llt_";
	static final int RANDOM_BYTES = 32;

	private final SecureRandom random;

	public TokenSecrets() {
		this(new SecureRandom());
	}

	TokenSecrets(SecureRandom random) {
		this.random = random;
	}

	/** {@code llt_} + 32 random bytes in URL-safe Base64 without padding (47 characters). */
	public String generate() {
		byte[] bytes = new byte[RANDOM_BYTES];
		random.nextBytes(bytes);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Lowercase hex SHA-256 of the UTF-8 bytes of the token: the same value the Collector computes. */
	public static String sha256Hex(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
