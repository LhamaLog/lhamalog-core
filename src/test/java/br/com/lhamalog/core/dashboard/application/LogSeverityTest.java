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

	@ParameterizedTest
	@CsvSource({
			"1, INFO",
			"9, INFO",
			"12, INFO",
			"13, WARNING",
			"16, WARNING",
			"17, ERROR",
			"21, ERROR",
			"24, ERROR"
	})
	void mapsOtelSeverityNumbers(int number, LogSeverity expected) {
		assertEquals(Optional.of(expected), LogSeverity.fromOtelNumber(number));
	}

	@Test
	void otelNumberZeroOrNullIsEmpty() {
		assertTrue(LogSeverity.fromOtelNumber(0).isEmpty());
		assertTrue(LogSeverity.fromOtelNumber(null).isEmpty());
		assertTrue(LogSeverity.fromOtelNumber(25).isEmpty());
	}

	@Test
	void fromOpenObservePrefersNumberThenTextThenInfo() {
		assertEquals(LogSeverity.ERROR, LogSeverity.fromOpenObserve(17, "info"));
		assertEquals(LogSeverity.WARNING, LogSeverity.fromOpenObserve(0, "warn"));
		assertEquals(LogSeverity.INFO, LogSeverity.fromOpenObserve(null, null));
		assertEquals(LogSeverity.INFO, LogSeverity.fromOpenObserve(0, "weird"));
	}

	@Test
	void parseFilterAcceptsOnlyNormalizedEnums() {
		assertEquals(Optional.of(LogSeverity.ERROR), LogSeverity.parseFilter("error"));
		assertEquals(Optional.of(LogSeverity.WARNING), LogSeverity.parseFilter("WARNING"));
		assertTrue(LogSeverity.parseFilter("warn").isEmpty());
		assertTrue(LogSeverity.parseFilter("fatal").isEmpty());
	}
}
