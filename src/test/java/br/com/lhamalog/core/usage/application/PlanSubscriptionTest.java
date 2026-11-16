package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanSubscriptionTest {

	@Test
	void legacyConstructorsDefaultToAtivaAndOverageDisabled() {
		PlanSubscription fiveArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1));
		assertThat(fiveArg.status()).isEqualTo(SubscriptionStatus.ATIVA);
		assertThat(fiveArg.overageEnabled()).isFalse();
		assertThat(fiveArg.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(fiveArg.unlimitedUsage()).isFalse();

		PlanSubscription sevenArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false);
		assertThat(sevenArg.status()).isEqualTo(SubscriptionStatus.ATIVA);
		assertThat(sevenArg.overageEnabled()).isFalse();
	}

	@Test
	void persistenceCreatorMapsNullStatusAndOverageToLegacyDefaults() {
		PlanSubscription legacy = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), null, null, null, null);
		assertThat(legacy.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(legacy.unlimitedUsage()).isFalse();
		assertThat(legacy.status()).isEqualTo(SubscriptionStatus.ATIVA);
		assertThat(legacy.overageEnabled()).isFalse();
	}

	@Test
	void explicitSuspendedAndOverageArePreserved() {
		PlanSubscription suspended = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.SUSPENSA, true);
		assertThat(suspended.status()).isEqualTo(SubscriptionStatus.SUSPENSA);
		assertThat(suspended.overageEnabled()).isTrue();
	}
}
