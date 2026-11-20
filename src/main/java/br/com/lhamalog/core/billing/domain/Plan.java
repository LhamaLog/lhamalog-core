package br.com.lhamalog.core.billing.domain;

import java.math.BigDecimal;
import java.util.Objects;

import br.com.lhamalog.core.usage.application.PlanTier;

/**
 * A plan offered for purchase (spec 010). Prices are configuration until the commercial model is closed (LLT-229).
 *
 * @param tier commercial tier
 * @param name display name
 * @param price monthly price in BRL, never negative
 */
public record Plan(PlanTier tier, String name, BigDecimal price) {

	public Plan {
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(price, "price");
		if (price.signum() < 0) {
			throw new IllegalArgumentException("price must not be negative");
		}
	}

}
