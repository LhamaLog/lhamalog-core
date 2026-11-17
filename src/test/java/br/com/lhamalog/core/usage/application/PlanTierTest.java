package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanTierTest {

	@Test
	void adr131RetentionDaysMatchTabelaPrecificacao() {
		assertThat(PlanTier.FREE.retentionDays()).isEqualTo(7);
		assertThat(PlanTier.STARTER.retentionDays()).isEqualTo(15);
		assertThat(PlanTier.PRO.retentionDays()).isEqualTo(30);
		assertThat(PlanTier.BUSINESS.retentionDays()).isEqualTo(90);
		assertThat(PlanTier.ENTERPRISE.retentionDays()).isEqualTo(90);
	}

	@Test
	void parseLenientAcceptsCaseInsensitiveEnglishNames() {
		assertThat(PlanTier.parseLenient("FREE")).isEqualTo(PlanTier.FREE);
		assertThat(PlanTier.parseLenient("free")).isEqualTo(PlanTier.FREE);
		assertThat(PlanTier.parseLenient("Starter")).isEqualTo(PlanTier.STARTER);
		assertThat(PlanTier.parseLenient("Pro")).isEqualTo(PlanTier.PRO);
		assertThat(PlanTier.parseLenient("Business")).isEqualTo(PlanTier.BUSINESS);
		assertThat(PlanTier.parseLenient("Enterprise")).isEqualTo(PlanTier.ENTERPRISE);
	}

	@Test
	void parseLenientReturnsNullForBlankOrUnknown() {
		assertThat(PlanTier.parseLenient(null)).isNull();
		assertThat(PlanTier.parseLenient("")).isNull();
		assertThat(PlanTier.parseLenient("   ")).isNull();
		assertThat(PlanTier.parseLenient("GOLD")).isNull();
	}
}
