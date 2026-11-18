package br.com.lhamalog.core.billing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import br.com.lhamalog.core.billing.domain.Plan;
import br.com.lhamalog.core.usage.application.PlanTier;

/** Plans that can be bought, in tier order. Only tiers with a configured price are offered (research R10). */
public class PlanCatalog {

	private final List<Plan> plans;

	public PlanCatalog(Map<PlanTier, BigDecimal> prices) {
		List<Plan> configured = new ArrayList<>();
		prices.forEach((tier, price) -> {
			if (price != null) {
				configured.add(new Plan(tier, displayName(tier), price.setScale(2, RoundingMode.HALF_UP)));
			}
		});
		configured.sort(Comparator.comparing(Plan::tier));
		this.plans = List.copyOf(configured);
	}

	public List<Plan> plans() {
		return plans;
	}

	/** Finds a plan by tier name, ignoring case and surrounding spaces. */
	public Optional<Plan> find(String tierName) {
		if (tierName == null) {
			return Optional.empty();
		}
		PlanTier tier = PlanTier.parseLenient(tierName);
		return plans.stream().filter((plan) -> plan.tier() == tier).findFirst();
	}

	static String displayName(PlanTier tier) {
		String lower = tier.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

}
