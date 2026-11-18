package br.com.lhamalog.core.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test-only Svix signer with a random secret generated in memory (never a real secret). Written independently from
 * the production verifier, following the Svix documentation.
 */
public final class TestSvix {

	public static final String SECRET = randomSecret();

	private TestSvix() {
	}

	public static String sign(String secret, String svixId, long timestampSeconds, String body) {
		try {
			byte[] key = Base64.getDecoder().decode(secret.substring("whsec_".length()));
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			byte[] signature = mac
				.doFinal((svixId + "." + timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
			return "v1," + Base64.getEncoder().encodeToString(signature);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static String sign(String svixId, long timestampSeconds, String body) {
		return sign(SECRET, svixId, timestampSeconds, body);
	}

	public static long now() {
		return Instant.now().getEpochSecond();
	}

	public static String randomSecret() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return "whsec_" + Base64.getEncoder().encodeToString(key);
	}

}
