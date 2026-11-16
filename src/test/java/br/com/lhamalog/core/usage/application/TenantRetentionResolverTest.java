package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantRetentionResolverTest {

	@Test
	void freeOnlyResolvesToSevenDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void freeAndProResolvesToMaxThirtyDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.ATIVA));
		repo.save(subscription("product-b", PlanTier.PRO, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(30);
	}

	@Test
	void legacySubscriptionWithoutTierCountsAsFree() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void delinquentLegacySubscriptionWithoutTierCountsAsFree() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.INADIMPLENTE));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(7);
	}

	@Test
	void legacyWithoutTierAndProResolvesToThirtyDays() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.ATIVA));
		repo.save(subscription("product-b", PlanTier.PRO, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(30);
	}

	@Test
	void suspendedLegacySubscriptionWithoutTierIsIgnored() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", null, SubscriptionStatus.SUSPENSA));
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
		repo.save(subscription("product-a", null, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo,
				new PlanRetentionProperties(Map.of("free", 10)));

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(10);
	}

	@Test
	void suspendedOnlyTierIsIgnored() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.ENTERPRISE, SubscriptionStatus.SUSPENSA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isNull();
	}

	@Test
	void includesDelinquentAndIgnoresSuspendedWhenComputingMax() {
		InMemoryPlanSubscriptionRepository repo = new InMemoryPlanSubscriptionRepository();
		repo.save(subscription("product-a", PlanTier.FREE, SubscriptionStatus.INADIMPLENTE));
		repo.save(subscription("product-b", PlanTier.BUSINESS, SubscriptionStatus.SUSPENSA));
		repo.save(subscription("product-c", PlanTier.STARTER, SubscriptionStatus.ATIVA));
		TenantRetentionResolver resolver = new TenantRetentionResolver(repo, defaults());

		assertThat(resolver.resolveRetentionDays("tenant")).isEqualTo(15);
	}

	private static PlanSubscription subscription(String productId, PlanTier tier, SubscriptionStatus status) {
		return new PlanSubscription("id-" + productId, "tenant", productId, PlanType.MONTHLY,
				LocalDate.of(2026, 11, 1), BigDecimal.valueOf(0.01), false, status, false, null, tier);
	}

	private static PlanRetentionProperties defaults() {
		return new PlanRetentionProperties(Map.of());
	}
}
