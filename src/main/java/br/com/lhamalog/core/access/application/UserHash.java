package br.com.lhamalog.core.access.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Short SHA-256 of a user id, so logs can correlate a person without carrying the id (constitution VI). */
public final class UserHash {

	private static final int LENGTH = 12;

	private UserHash() {
	}

	public static String of(String userId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(userId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, LENGTH);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java runtime", ex);
		}
	}

}
