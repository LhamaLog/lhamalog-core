package br.com.lhamalog.core.dashboard.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves OpenObserve log stream names per tenant — same algorithm as the Collector
 * ({@code TenantStreamNames.resolve}), LLT-185.
 */
public final class OpenObserveStreamNames {

	private static final int MAX_SEGMENT_LENGTH = 40;

	private OpenObserveStreamNames() {
	}

	/**
	 * @param prefix stream prefix (Collector env {@code OPENOBSERVE_LOGS_STREAM}, default {@code logs})
	 * @param tenantId original tenant id (UTF-8; used for hash when sanitized/truncated)
	 */
	public static String resolve(String prefix, String tenantId) {
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(tenantId, "tenantId");
		if (prefix.isBlank()) {
			throw new IllegalArgumentException("prefix must not be blank");
		}
		String lower = tenantId.toLowerCase(Locale.ROOT);
		StringBuilder sanitized = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
				sanitized.append(c);
			}
			else {
				sanitized.append('_');
			}
		}
		String full = sanitized.toString();
		boolean truncated = full.length() > MAX_SEGMENT_LENGTH;
		String segment = truncated ? full.substring(0, MAX_SEGMENT_LENGTH) : full;
		boolean changed = !full.equals(lower) || truncated;
		if (changed) {
			return prefix + "_" + segment + "_" + shortHash(tenantId);
		}
		return prefix + "_" + segment;
	}

	/** First 8 hex chars (4 bytes) of SHA-256(tenantId UTF-8), lowercase. */
	static String shortHash(String tenantId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(tenantId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 4);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
