package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanSubscriptionTest {

	@Test
	void legacyConstructorsDefaultToActiveOverageDisabledAndNullTier() {
		PlanSubscription fiveArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1));
		assertThat(fiveArg.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(fiveArg.overageEnabled()).isFalse();
		assertThat(fiveArg.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(fiveArg.unlimitedUsage()).isFalse();
		assertThat(fiveArg.delinquentSince()).isNull();
		assertThat(fiveArg.planTier()).isNull();

		PlanSubscription sevenArg = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false);
		assertThat(sevenArg.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(sevenArg.overageEnabled()).isFalse();
		assertThat(sevenArg.delinquentSince()).isNull();
		assertThat(sevenArg.planTier()).isNull();
	}

	@Test
	void persistenceCreatorMapsNullStatusOverageDelinquentSinceAndTierToLegacyDefaults() {
		PlanSubscription legacy = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), null, null, null, null, null, null);
		assertThat(legacy.allowanceGigabytes()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(legacy.unlimitedUsage()).isFalse();
		assertThat(legacy.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(legacy.overageEnabled()).isFalse();
		assertThat(legacy.delinquentSince()).isNull();
		assertThat(legacy.planTier()).isNull();
	}

	@Test
	void persistenceCreatorAcceptsParsedPlanTier() {
		PlanSubscription subscription = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.ACTIVE, false, null,
				PlanTier.parseLenient("Starter"));
		assertThat(subscription.planTier()).isEqualTo(PlanTier.STARTER);
	}

	@Test
	void explicitSuspendedOverageDelinquentSinceAndTierArePreserved() {
		Instant since = Instant.parse("2026-11-01T00:00:00Z");
		PlanSubscription suspended = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.SUSPENDED, true, since,
				PlanTier.PRO);
		assertThat(suspended.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
		assertThat(suspended.overageEnabled()).isTrue();
		assertThat(suspended.delinquentSince()).isEqualTo(since);
		assertThat(suspended.planTier()).isEqualTo(PlanTier.PRO);
	}

	@Test
	void nineArgConvenienceDefaultsDelinquentSinceAndTierToNull() {
		PlanSubscription subscription = new PlanSubscription("id", "tenant", "product", PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.DELINQUENT, false);
		assertThat(subscription.delinquentSince()).isNull();
		assertThat(subscription.planTier()).isNull();
		assertThat(subscription.status()).isEqualTo(SubscriptionStatus.DELINQUENT);
	}
}
