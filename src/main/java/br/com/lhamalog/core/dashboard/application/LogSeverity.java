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
	 * Maps an OpenTelemetry severity number (1–24) to ADR_170 buckets:
	 * 17–24 → ERROR (includes FATAL), 13–16 → WARNING, 1–12 → INFO.
	 * {@code null} or {@code 0} yield empty (callers fall back to text / INFO).
	 */
	public static Optional<LogSeverity> fromOtelNumber(Integer severityNumber) {
		if (severityNumber == null || severityNumber == 0) {
			return Optional.empty();
		}
		int n = severityNumber;
		if (n >= 17 && n <= 24) {
			return Optional.of(ERROR);
		}
		if (n >= 13 && n <= 16) {
			return Optional.of(WARNING);
		}
		if (n >= 1 && n <= 12) {
			return Optional.of(INFO);
		}
		return Optional.empty();
	}

	/**
	 * Resolves severity from OTel number first, then free-text {@code severityText}.
	 * When both are absent/unusable → {@link #INFO} (Collector always writes a number 1–24;
	 * 0/missing is treated as INFO after text fallback).
	 */
	public static LogSeverity fromOpenObserve(Integer severityNumber, String severityText) {
		return fromOtelNumber(severityNumber)
				.or(() -> fromRaw(severityText))
				.orElse(INFO);
	}

	/**
	 * Maps a raw severity/level string to the ADR_170 enum.
	 * Unknown or blank values yield empty (callers decide 400 vs skip / INFO).
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
