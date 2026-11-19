package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LogSeverityTest {

	@ParameterizedTest
	@CsvSource({
			"error, ERROR",
			"ERRO, ERROR",
			"fatal, ERROR",
			"critical, ERROR",
			"warn, WARNING",
			"WARNING, WARNING",
			"alerta, WARNING",
			"info, INFO",
			"DEBUG, INFO",
			"trace, INFO"
	})
	void mapsAdr170RawValues(String raw, LogSeverity expected) {
		assertEquals(Optional.of(expected), LogSeverity.fromRaw(raw));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  ", "unknown", "silly"})
	void rejectsUnknownRawValues(String raw) {
		assertTrue(LogSeverity.fromRaw(raw).isEmpty());
	}

	@Test
	void parseFilterAcceptsOnlyNormalizedEnums() {
		assertEquals(Optional.of(LogSeverity.ERROR), LogSeverity.parseFilter("error"));
		assertEquals(Optional.of(LogSeverity.WARNING), LogSeverity.parseFilter("WARNING"));
		assertTrue(LogSeverity.parseFilter("warn").isEmpty());
		assertTrue(LogSeverity.parseFilter("fatal").isEmpty());
	}
}
