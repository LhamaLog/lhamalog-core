package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.OccurrenceSignature;

/** Spec 012, research R13: parts of the Finder signature and the fallback title. */
class OccurrenceSignatureTest {

	@Test
	void splitsTheFourParts() {
		OccurrenceSignature signature = OccurrenceSignature.parse("ERROR|connection timeout|user-service|host-1");
		assertThat(signature.message()).isEqualTo("connection timeout");
		assertThat(signature.service()).isEqualTo("user-service");
		assertThat(signature.fallbackTitle()).isEqualTo("connection timeout");
	}

	@Test
	void escapedPipeStaysInsideThePart() {
		OccurrenceSignature signature = OccurrenceSignature.parse("ERROR|gateway \\| retry later|payment|host-1");
		assertThat(signature.message()).isEqualTo("gateway | retry later");
		assertThat(signature.service()).isEqualTo("payment");
	}

	@Test
	void signatureOutOfFormatIsTheWholeTitleAndHasNoService() {
		OccurrenceSignature signature = OccurrenceSignature.parse("only|three|parts");
		assertThat(signature.message()).isNull();
		assertThat(signature.service()).isNull();
		assertThat(signature.fallbackTitle()).isEqualTo("only|three|parts");
	}

	@Test
	void blankMessageFallsBackToTheSignature() {
		assertThat(OccurrenceSignature.parse("INFO||svc|host").fallbackTitle()).isEqualTo("INFO||svc|host");
	}

	@Test
	void nullSignatureHasNoParts() {
		OccurrenceSignature signature = OccurrenceSignature.parse(null);
		assertThat(signature.fallbackTitle()).isNull();
		assertThat(signature.service()).isNull();
	}
}
