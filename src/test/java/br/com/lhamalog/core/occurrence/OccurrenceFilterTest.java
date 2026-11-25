package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.OccurrenceFilter;
import br.com.lhamalog.core.occurrence.application.OccurrenceStatus;
import br.com.lhamalog.core.occurrence.application.OccurrenceType;
import br.com.lhamalog.core.shared.FieldValidationException;

/** Spec 012: validation of the list parameters (contracts/api.md, 400 messages). */
class OccurrenceFilterTest {

	private static OccurrenceFilter parse(String page, String size) {
		return OccurrenceFilter.parse(page, size, null, null, null, null, null, null);
	}

	private static FieldValidationException invalid(Runnable parse) {
		return catchThrowableOfType(FieldValidationException.class, parse::run);
	}

	// US1

	@Test
	void defaultsArePageOneAndSizeTwenty() {
		OccurrenceFilter filter = parse(null, null);
		assertThat(filter.page()).isEqualTo(1);
		assertThat(filter.size()).isEqualTo(20);
		assertThat(filter.types()).isEmpty();
		assertThat(filter.text()).isNull();
	}

	@Test
	void acceptsTheThreeSizes() {
		assertThat(parse("3", "10").size()).isEqualTo(10);
		assertThat(parse("3", "20").size()).isEqualTo(20);
		assertThat(parse("3", "50").size()).isEqualTo(50);
		assertThat(parse("3", "50").page()).isEqualTo(3);
	}

	@Test
	void refusesOtherSizesAndPagesBelowOne() {
		FieldValidationException error = invalid(() -> parse("0", "15"));
		assertThat(error.fieldErrors()).containsEntry("size", "Use 10, 20 ou 50.")
			.containsEntry("page", "A página começa em 1.");
		assertThat(invalid(() -> parse("abc", null)).fieldErrors()).containsKey("page");
	}

	// US2

	@Test
	void repeatedTypesAndStatusesAreCombined() {
		OccurrenceFilter filter = OccurrenceFilter.parse(null, null, List.of("ERROR", "WARNING", "ERROR"),
				List.of("ABERTA", "EM_ANALISE"), List.of("p1", "p2"), null, null, null);
		assertThat(filter.types()).containsExactlyInAnyOrder(OccurrenceType.ERROR, OccurrenceType.WARNING);
		assertThat(filter.statuses()).containsExactlyInAnyOrder(OccurrenceStatus.ABERTA, OccurrenceStatus.EM_ANALISE);
		assertThat(filter.productIds()).containsExactlyInAnyOrder("p1", "p2");
	}

	@Test
	void unknownTypeOrStatusIsRefused() {
		FieldValidationException error = invalid(() -> OccurrenceFilter.parse(null, null, List.of("error"),
				List.of("RESOLVIDA"), null, null, null, null));
		assertThat(error.fieldErrors()).containsEntry("type", "Tipo desconhecido. Use ERROR, WARNING ou INFO.")
			.containsEntry("status", "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA.");
	}

	@Test
	void periodAcceptsOffsetsAndRefusesBadOrInvertedDates() {
		OccurrenceFilter filter = OccurrenceFilter.parse(null, null, null, null, null, "2026-11-12T00:00:00-03:00",
				"2026-11-13T00:00:00Z", null);
		assertThat(filter.from()).isEqualTo(Instant.parse("2026-11-12T03:00:00Z"));
		assertThat(filter.to()).isEqualTo(Instant.parse("2026-11-13T00:00:00Z"));
		// "+03:00" not encoded in the URL arrives as a space.
		assertThat(OccurrenceFilter.parse(null, null, null, null, null, "2026-11-12T00:00:00 03:00", null, null)
			.from()).isEqualTo(Instant.parse("2026-11-11T21:00:00Z"));
		assertThat(invalid(() -> OccurrenceFilter.parse(null, null, null, null, null, "ontem", null, null))
			.fieldErrors()).containsEntry("from", "Data inválida. Use o formato ISO-8601 com fuso.");
		assertThat(invalid(() -> OccurrenceFilter.parse(null, null, null, null, null, "2026-11-13T00:00:00Z",
				"2026-11-12T00:00:00Z", null)).fieldErrors())
			.containsEntry("to", "O fim do período precisa ser depois do início.");
	}

	@Test
	void searchIsTrimmedLimitedAndBlankMeansNoSearch() {
		assertThat(OccurrenceFilter.parse(null, null, null, null, null, null, null, "  timeout ").text())
			.isEqualTo("timeout");
		assertThat(OccurrenceFilter.parse(null, null, null, null, null, null, null, "   ").text()).isNull();
		assertThat(invalid(() -> OccurrenceFilter.parse(null, null, null, null, null, null, null, "x".repeat(201)))
			.fieldErrors()).containsEntry("q", "A busca aceita até 200 caracteres.");
	}
}
