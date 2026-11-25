package br.com.lhamalog.core.occurrence.application;

import java.util.Optional;

/** Lifecycle status of an occurrence (ADR_082). "Recurring" is an attribute, not a status (ADR_063). */
public enum OccurrenceStatus {
	ABERTA, EM_ANALISE, FECHADA;

	/** Exact upper-case name only; anything else is empty, never an exception. */
	public static Optional<OccurrenceStatus> parse(String value) {
		if (value == null) return Optional.empty();
		for (OccurrenceStatus status : values()) {
			if (status.name().equals(value)) return Optional.of(status);
		}
		return Optional.empty();
	}
}
