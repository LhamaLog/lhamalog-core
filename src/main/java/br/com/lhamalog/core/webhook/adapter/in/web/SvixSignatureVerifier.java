package br.com.lhamalog.core.webhook.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies Svix webhook signatures with the JDK only (no Svix SDK), following
 * https://docs.svix.com/receiving/verifying-payloads/how-manual: HMAC-SHA256 over {@code id.timestamp.rawBody}, keyed
 * by the Base64 part of the {@code whsec_} secret, compared in constant time; the timestamp must be within tolerance.
 */
public final class SvixSignatureVerifier {

	private static final Logger log = LoggerFactory.getLogger(SvixSignatureVerifier.class);

	private static final String SECRET_PREFIX = "whsec_";

	private static final String SIGNATURE_VERSION = "v1,";

	private static final String HMAC = "HmacSHA256";

	private final byte[] key;

	private final Duration tolerance;

	private final Clock clock;

	public SvixSignatureVerifier(String signingSecret, Duration tolerance, Clock clock) {
		this.key = decodeSecret(signingSecret);
		this.tolerance = tolerance;
		this.clock = clock;
		if (key == null) {
			log.warn("CLERK_WEBHOOK_SIGNING_SECRET is not configured or invalid; Clerk webhooks will be rejected");
		}
	}

	/** @return {@code true} only for an authentic, recent message */
	public boolean verify(String id, String timestamp, String signatureHeader, byte[] body) {
		if (key == null || isBlank(id) || isBlank(timestamp) || isBlank(signatureHeader) || body == null) {
			return false;
		}
		if (!isRecent(timestamp)) {
			return false;
		}
		byte[] expected = sign(id, timestamp, body);
		for (String candidate : signatureHeader.trim().split(" +")) {
			if (candidate.startsWith(SIGNATURE_VERSION)) {
				byte[] provided = decodeBase64(candidate.substring(SIGNATURE_VERSION.length()));
				if (provided != null && MessageDigest.isEqual(expected, provided)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRecent(String timestamp) {
		long seconds;
		try {
			seconds = Long.parseLong(timestamp.trim());
		}
		catch (NumberFormatException ex) {
			return false;
		}
		Instant sentAt = Instant.ofEpochSecond(seconds);
		Instant now = clock.instant();
		return !sentAt.isBefore(now.minus(tolerance)) && !sentAt.isAfter(now.plus(tolerance));
	}

	private byte[] sign(String id, String timestamp, byte[] body) {
		try {
			Mac mac = Mac.getInstance(HMAC);
			mac.init(new SecretKeySpec(key, HMAC));
			mac.update((id + "." + timestamp.trim() + ".").getBytes(StandardCharsets.UTF_8));
			return mac.doFinal(body);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

	private static byte[] decodeSecret(String secret) {
		if (isBlank(secret)) {
			return null;
		}
		String value = secret.trim();
		if (value.startsWith(SECRET_PREFIX)) {
			value = value.substring(SECRET_PREFIX.length());
		}
		byte[] decoded = decodeBase64(value);
		return (decoded != null && decoded.length > 0) ? decoded : null;
	}

	private static byte[] decodeBase64(String value) {
		try {
			return Base64.getDecoder().decode(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
