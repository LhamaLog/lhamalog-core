package br.com.lhamalog.core.billing.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Token that authenticates the Asaas webhook ({@code asaas-access-token}, research R8). Asaas accepts 32 to 255
 * characters without spaces. The value is never logged or printed.
 */
public final class AsaasWebhookCredential {

	static final int MIN_LENGTH = 32;

	static final int MAX_LENGTH = 255;

	private final byte[] token;

	private AsaasWebhookCredential(String token) {
		this.token = token.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Credential of a real environment: the token is mandatory.
	 * @throws IllegalStateException when the token is missing or not a valid Asaas token
	 */
	public static AsaasWebhookCredential configured(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("ASAAS_WEBHOOK_TOKEN is required outside the local profile");
		}
		return validated(token);
	}

	/**
	 * Credential of the local profile: a blank token is replaced by a random one, which the simulator also uses, so
	 * nothing has to be configured and no token is versioned.
	 */
	public static AsaasWebhookCredential generatedWhenBlank(String token) {
		if (token == null || token.isBlank()) {
			byte[] random = new byte[24];
			new SecureRandom().nextBytes(random);
			return new AsaasWebhookCredential(HexFormat.of().formatHex(random));
		}
		return validated(token);
	}

	private static AsaasWebhookCredential validated(String token) {
		if (token.length() < MIN_LENGTH || token.length() > MAX_LENGTH || token.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalStateException(
					"ASAAS_WEBHOOK_TOKEN must have 32 to 255 characters and no spaces (length=" + token.length() + ")");
		}
		return new AsaasWebhookCredential(token);
	}

	/** Constant-time comparison with the presented header value. */
	public boolean matches(String presented) {
		if (presented == null) {
			return false;
		}
		return MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
	}

	/** The token itself, for the local simulator that signs its own deliveries. */
	public String value() {
		return new String(token, StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "AsaasWebhookCredential[***]";
	}

}
