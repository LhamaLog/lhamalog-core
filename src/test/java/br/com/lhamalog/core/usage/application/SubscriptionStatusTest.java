package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class SubscriptionStatusTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@ParameterizedTest
	@CsvSource({
			"ACTIVE, ACTIVE",
			"active, ACTIVE",
			"ATIVA, ACTIVE",
			"ativa, ACTIVE",
			"SUSPENDED, SUSPENDED",
			"SUSPENSA, SUSPENDED",
			"suspensa, SUSPENDED",
			"DELINQUENT, DELINQUENT",
			"INADIMPLENTE, DELINQUENT",
			"inadimplente, DELINQUENT"
	})
	void fromPersistedAcceptsEnglishAndPortugueseSpellings(String raw, SubscriptionStatus expected) {
		assertThat(SubscriptionStatus.fromPersisted(raw)).isEqualTo(expected);
	}

	@Test
	void fromPersistedRejectsUnknownValues() {
		assertThatThrownBy(() -> SubscriptionStatus.fromPersisted("CANCELADA"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void jacksonAcceptsPortugueseAndEmitsEnglish() throws Exception {
		assertThat(mapper.readValue("\"SUSPENSA\"", SubscriptionStatus.class))
				.isEqualTo(SubscriptionStatus.SUSPENDED);
		assertThat(mapper.readValue("\"INADIMPLENTE\"", SubscriptionStatus.class))
				.isEqualTo(SubscriptionStatus.DELINQUENT);
		assertThat(mapper.readValue("\"ATIVA\"", SubscriptionStatus.class)).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(mapper.writeValueAsString(SubscriptionStatus.ACTIVE)).isEqualTo("\"ACTIVE\"");
		assertThat(mapper.writeValueAsString(SubscriptionStatus.SUSPENDED)).isEqualTo("\"SUSPENDED\"");
		assertThat(mapper.writeValueAsString(SubscriptionStatus.DELINQUENT)).isEqualTo("\"DELINQUENT\"");
	}
}
