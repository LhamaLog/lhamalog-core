package br.com.lhamalog.core.occurrence.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent.Change;

/** Spec 016: the Redis notice carries the class as {@code gravidade} and the change name. */
class RedisOccurrenceEventPublisherTest {

	@Test
	void messageCarriesSeverityAndTheChangeName() {
		OccurrenceEvent event = new OccurrenceEvent("t-1", "p-1", "o-1", "INFO", "INFO", Change.RECURRENT,
				Instant.parse("2026-11-26T17:00:00Z"));
		Map<String, Object> message = RedisOccurrenceEventPublisher.toMessage(event);
		assertThat(message).containsEntry("tenantId", "t-1")
			.containsEntry("productId", "p-1")
			.containsEntry("occurrenceId", "o-1")
			.containsEntry("occurrenceType", "INFO")
			.containsEntry("gravidade", "INFO")
			.containsEntry("change", "RECURRENT")
			.containsEntry("at", "2026-11-26T17:00:00Z");
		assertThat(message).doesNotContainKey("critico");
	}

}
