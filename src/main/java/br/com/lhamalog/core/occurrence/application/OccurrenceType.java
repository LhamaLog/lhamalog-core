package br.com.lhamalog.core.occurrence.application;

import java.util.Optional;

/** Class of an occurrence, as sent by the Finder (contract DN-009-2). */
public enum OccurrenceType {
	ERROR, WARNING, INFO;

	/** Exact upper-case name only; anything else is empty, never an exception. */
	public static Optional<OccurrenceType> parse(String value) {
		if (value == null) return Optional.empty();
		for (OccurrenceType type : values()) {
			if (type.name().equals(value)) return Optional.of(type);
		}
		return Optional.empty();
	}
}
