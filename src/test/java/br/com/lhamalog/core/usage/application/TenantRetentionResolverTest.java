package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantRetentionResolverTest {

	@Test
	void freeOnlyResolvesToSevenDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void freeAndProResolvesToMaxThirtyDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.ACTIVE));
		repo.save(subscription("product-b", PlanTier.PRO, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(30);
	}

	@Test
	void legacySubscriptionWithoutTierCountsAsFree() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void delinquentLegacySubscriptionWithoutTierCountsAsFree() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.DELINQUENT));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void legacyWithoutTierAndProResolvesToThirtyDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.ACTIVE));
		repo.save(subscription("product-b", PlanTier.PRO, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(30);
	}

	@Test
	void suspendedLegacySubscriptionWithoutTierIsIgnored() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.SUSPENDED));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isNull();
	}

	@Test
	void tenantWithoutSubscriptionsResolvesToNull() {
		TenantRetentionResolver resolver = new TenantRetentionResolver(new InMemoryPlanSubscriptionRepository(),
				defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isNull();
	}

	@Test
	void freeOverrideAppliesToLegacySubscriptionWithoutTier() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo,
				new PlanRetentionProperties(Map.of("free", 10)));

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(10);
	}

	@Test
	void suspendedOnlyTierIsIgnored() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.ENTERPRISE, SubscriptionStatus.SUSPENDED));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isNull();
	}

	@Test
	void includesDelinquentAndIgnoresSuspendedWhenComputingMax() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.DELINQUENT));
		repo.save(subscription("product-b", PlanTier.BUSINESS, SubscriptionStatus.SUSPENDED));
		repo.save(subscription("product-c", PlanTier.STARTER, SubscriptionStatus.ACTIVE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(15);
	}

	private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
	private static final Duration GRACE = Duration.ofDays(15);

	@Test
	void businessDelinquentPastGracePlusFreeActiveResolvesToSevenDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.BUSINESS, NOW.minus(Duration.ofDays(30))));
		repo.save(subscription("product-b", PlanTier.FREE, SubscriptionStatus.ACTIVE));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void delinquentWithinGraceStillCounts() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.BUSINESS, NOW.minus(Duration.ofDays(5))));
		repo.save(subscription("product-b", PlanTier.FREE, SubscriptionStatus.ACTIVE));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isEqualTo(90);
	}

	@Test
	void delinquentExactlyAtGraceEndIsExcluded() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.BUSINESS, NOW.minus(GRACE)));
		repo.save(subscription("product-b", PlanTier.FREE, SubscriptionStatus.ACTIVE));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void delinquentOneSecondBeforeGraceEndStillCounts() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.BUSINESS, NOW.minus(GRACE).plusSeconds(1)));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isEqualTo(90);
	}

	@Test
	void onlyDelinquentPastGraceResolvesToNull() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.PRO, NOW.minus(Duration.ofDays(30))));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isNull();
	}

	@Test
	void delinquentWithoutDelinquentSinceCountsConservatively() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(delinquent("product-a", PlanTier.PRO, null));

		assertThat(atNow(repo).resolveRetentionDays("tenant")).isEqualTo(30);
	}

	private static TenantRetentionResolver atNow(InMemoryPlanSubscriptionRepository repo) {
		return new TenantRetentionResolver(repo, defaults(), GRACE, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static PlanSubscription delinquent(String productId, PlanTier tier, Instant delinquentSince) {
		return new PlanSubscription("id-" + productId, "tenant", productId, PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, SubscriptionStatus.DELINQUENT, false,
				delinquentSince, tier);
	}

	private static PlanSubscription subscription(String productId, PlanTier tier, SubscriptionStatus status) {
		return new PlanSubscription("id-" + productId, "tenant", productId, PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, status, false, null, tier);
	}

	private static PlanRetentionProperties defaults() {
		return new PlanRetentionProperties(Map.of());
	}
}
