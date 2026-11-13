package br.com.lhamalog.core.webhook.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.support.TestSvix;

class SvixSignatureVerifierTests {

	private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

	private static final String SECRET = TestSvix.randomSecret();

	private static final String ID = "msg_test";

	private static final String BODY = "{\"type\":\"user.created\",\"data\":{}}";

	private final SvixSignatureVerifier verifier = new SvixSignatureVerifier(SECRET, Duration.ofMinutes(5),
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final long timestamp = NOW.getEpochSecond();

	@Test
	void acceptsValidSignature() {
		assertThat(verify(ID, timestamp, TestSvix.sign(SECRET, ID, timestamp, BODY), BODY)).isTrue();
	}

	@Test
	void acceptsWhenOneOfSeveralSignaturesMatches() {
		String header = TestSvix.sign(TestSvix.randomSecret(), ID, timestamp, BODY) + " "
				+ TestSvix.sign(SECRET, ID, timestamp, BODY);
		assertThat(verify(ID, timestamp, header, BODY)).isTrue();
	}

	@Test
	void rejectsSignatureMadeWithAnotherSecret() {
		assertThat(verify(ID, timestamp, TestSvix.sign(TestSvix.randomSecret(), ID, timestamp, BODY), BODY)).isFalse();
	}

	@Test
	void rejectsTamperedBody() {
		assertThat(verify(ID, timestamp, TestSvix.sign(SECRET, ID, timestamp, BODY), BODY + " ")).isFalse();
	}

	@Test
	void rejectsSignatureOfAnotherMessageId() {
		assertThat(verify("msg_other", timestamp, TestSvix.sign(SECRET, ID, timestamp, BODY), BODY)).isFalse();
	}

	@Test
	void rejectsOldTimestamp() {
		long old = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();
		assertThat(verify(ID, old, TestSvix.sign(SECRET, ID, old, BODY), BODY)).isFalse();
	}

	@Test
	void rejectsFutureTimestamp() {
		long future = NOW.plus(Duration.ofMinutes(6)).getEpochSecond();
		assertThat(verify(ID, future, TestSvix.sign(SECRET, ID, future, BODY), BODY)).isFalse();
	}

	@Test
	void acceptsTimestampInsideTolerance() {
		long recent = NOW.minus(Duration.ofMinutes(4)).getEpochSecond();
		assertThat(verify(ID, recent, TestSvix.sign(SECRET, ID, recent, BODY), BODY)).isTrue();
	}

	@Test
	void rejectsMissingHeaders() {
		String signature = TestSvix.sign(SECRET, ID, timestamp, BODY);
		byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
		assertThat(verifier.verify(null, String.valueOf(timestamp), signature, body)).isFalse();
		assertThat(verifier.verify(ID, null, signature, body)).isFalse();
		assertThat(verifier.verify(ID, String.valueOf(timestamp), null, body)).isFalse();
		assertThat(verifier.verify(ID, String.valueOf(timestamp), signature, null)).isFalse();
	}

	@Test
	void rejectsNonNumericTimestamp() {
		assertThat(verifier.verify(ID, "yesterday", TestSvix.sign(SECRET, ID, timestamp, BODY),
				BODY.getBytes(StandardCharsets.UTF_8)))
			.isFalse();
	}

	@Test
	void ignoresOtherSignatureVersions() {
		String v1 = TestSvix.sign(SECRET, ID, timestamp, BODY);
		assertThat(verify(ID, timestamp, "v2," + v1.substring(3), BODY)).isFalse();
	}

	@Test
	void rejectsEverythingWithoutSecret() {
		SvixSignatureVerifier unconfigured = new SvixSignatureVerifier("", Duration.ofMinutes(5),
				Clock.fixed(NOW, ZoneOffset.UTC));
		assertThat(unconfigured.verify(ID, String.valueOf(timestamp), TestSvix.sign(SECRET, ID, timestamp, BODY),
				BODY.getBytes(StandardCharsets.UTF_8)))
			.isFalse();
	}

	private boolean verify(String id, long ts, String signature, String body) {
		return verifier.verify(id, String.valueOf(ts), signature, body.getBytes(StandardCharsets.UTF_8));
	}

}
