package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanSubscriptionTest {

	@Test
	void legacyConstructorsDefaultToActiveAndOverageDisabled() {
		PlanSubscription fiveArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1));
		assertThat(fiveArg.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(fiveArg.overageEnabled()).isFalse();
		assertThat(fiveArg.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(fiveArg.unlimitedUsage()).isFalse();
		assertThat(fiveArg.delinquentSince()).isNull();

		PlanSubscription sevenArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false);
		assertThat(sevenArg.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(sevenArg.overageEnabled()).isFalse();
		assertThat(sevenArg.delinquentSince()).isNull();
	}

	@Test
	void persistenceCreatorMapsNullStatusOverageAndDelinquentSinceToLegacyDefaults() {
		PlanSubscription legacy = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), null, null, null, null, null);
		assertThat(legacy.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(legacy.unlimitedUsage()).isFalse();
		assertThat(legacy.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(legacy.overageEnabled()).isFalse();
		assertThat(legacy.delinquentSince()).isNull();
	}

	@Test
	void explicitSuspendedOverageAndDelinquentSinceArePreserved() {
		Instant since = Instant.parse("2026-11-01T00:00:00Z");
		PlanSubscription suspended = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.SUSPENDED, true, since);
		assertThat(suspended.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
		assertThat(suspended.overageEnabled()).isTrue();
		assertThat(suspended.delinquentSince()).isEqualTo(since);
	}

	@Test
	void nineArgConvenienceDefaultsDelinquentSinceToNull() {
		PlanSubscription subscription = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.DELINQUENT, false);
		assertThat(subscription.delinquentSince()).isNull();
		assertThat(subscription.status()).isEqualTo(SubscriptionStatus.DELINQUENT);
	}
}
