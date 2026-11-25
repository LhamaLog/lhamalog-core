package br.com.lhamalog.core.occurrence.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Parts of the Finder signature {@code level|message|service|host} (DN-008-8 A), where a literal {@code |} inside a
 * part is written as {@code \|} (DN-008-9 A). The normalized message has no field of its own in the contract, so it
 * is the fallback title of an occurrence without {@code titulo}, and the service name only exists here (research R13).
 *
 * @param raw the stored signature
 * @param message second part, or {@code null} when the signature does not have exactly four parts
 * @param service third part, or {@code null} when the signature does not have exactly four parts
 */
public record OccurrenceSignature(String raw, String message, String service) {

	public static OccurrenceSignature parse(String raw) {
		if (raw == null) return new OccurrenceSignature(null, null, null);
		List<String> parts = split(raw);
		if (parts.size() != 4) return new OccurrenceSignature(raw, null, null);
		return new OccurrenceSignature(raw, parts.get(1), parts.get(2));
	}

	/** Title shown when the occurrence has none: the normalized message, or the whole signature. */
	public String fallbackTitle() {
		return message != null && !message.isBlank() ? message : raw;
	}

	private static List<String> split(String raw) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '|') {
				current.append('|');
				i++;
			}
			else if (c == '|') {
				parts.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(c);
			}
		}
		parts.add(current.toString());
		return parts;
	}
}
