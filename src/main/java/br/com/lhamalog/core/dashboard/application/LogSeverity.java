package br.com.lhamalog.core.dashboard.application;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalized log severity for dashboard metrics (ADR_170).
 * Raw values from OpenObserve are mapped into these three enums.
 */
public enum LogSeverity {
	ERROR, WARNING, INFO;

	/**
	 * Maps a raw severity/level string to the ADR_170 enum.
	 * Unknown or blank values yield empty (callers decide 400 vs skip).
	 */
	public static Optional<LogSeverity> fromRaw(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "error", "erro", "fatal", "crit", "critical", "panic" -> Optional.of(ERROR);
			case "warning", "warn", "alerta", "alert" -> Optional.of(WARNING);
			case "info", "information", "debug", "trace", "notice" -> Optional.of(INFO);
			default -> Optional.empty();
		};
	}

	/** Parses a query filter token; only ERROR|WARNING|INFO (case-insensitive). */
	public static Optional<LogSeverity> parseFilter(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		String normalized = token.trim().toUpperCase(Locale.ROOT);
		for (LogSeverity severity : values()) {
			if (severity.name().equals(normalized)) {
				return Optional.of(severity);
			}
		}
		return Optional.empty();
	}
}
