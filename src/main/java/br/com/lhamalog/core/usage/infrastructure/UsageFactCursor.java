package br.com.lhamalog.core.usage.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record UsageFactCursor(Instant acceptedAt, String id) {
	public static UsageFactCursor decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int separator = raw.indexOf('|');
			if (separator <= 0 || separator == raw.length() - 1) {
				throw new IllegalArgumentException("Invalid cursor");
			}
			return new UsageFactCursor(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
					raw.substring(separator + 1));
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid cursor", exception);
		}
	}

	public String encode() {
		String raw = acceptedAt.toEpochMilli() + "|" + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
