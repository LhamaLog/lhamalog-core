package br.com.lhamalog.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceTokenNormalizeTest {

	@Test
	void stripsWhitespaceCrlfAndSurroundingQuotes() {
		assertThat(UsageFactsSecurityConfiguration.normalizeToken("  secret\r\n ")).isEqualTo("secret");
		assertThat(UsageFactsSecurityConfiguration.normalizeToken("\"secret\"")).isEqualTo("secret");
		assertThat(UsageFactsSecurityConfiguration.normalizeToken("'secret'")).isEqualTo("secret");
		assertThat(UsageFactsSecurityConfiguration.sha256Hex("secret")).hasSize(64);
		assertThat(UsageFactsSecurityConfiguration.sha256Hex("secret"))
				.isNotEqualTo(UsageFactsSecurityConfiguration.sha256Hex("other"));
	}
}
