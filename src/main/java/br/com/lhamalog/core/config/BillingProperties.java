package br.com.lhamalog.core.config;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import br.com.lhamalog.core.usage.application.PlanTier;

/**
 * Purchase settings (spec 010).
 *
 * @param returnUrl where the browser goes back after the payment
 * @param plans price per tier name ({@code free}, {@code starter}, ...); tiers without price are not offered
 * @param asaas Asaas webhook settings
 * @param fake Asaas simulator settings (local profile only)
 */
@ConfigurationProperties("lhamalog.billing")
public record BillingProperties(String returnUrl, Map<String, PlanPrice> plans, Asaas asaas, Fake fake) {

	public BillingProperties {
		plans = (plans != null) ? plans : Map.of();
		asaas = (asaas != null) ? asaas : new Asaas(null);
		fake = (fake != null) ? fake : new Fake(null, null);
	}

	/** Prices by tier; unknown tier names are ignored. */
	public Map<PlanTier, BigDecimal> prices() {
		Map<PlanTier, BigDecimal> prices = new EnumMap<>(PlanTier.class);
		plans.forEach((name, plan) -> {
			PlanTier tier = PlanTier.parseLenient(name);
			if (tier != null && plan != null && plan.price() != null) {
				prices.put(tier, plan.price());
			}
		});
		return prices;
	}

	public record PlanPrice(BigDecimal price) {
	}

	/** @param webhookToken token Asaas sends in {@code asaas-access-token}; never printed */
	public record Asaas(String webhookToken) {

		@Override
		public String toString() {
			boolean configured = webhookToken != null && !webhookToken.isBlank();
			return "Asaas[webhookToken=" + (configured ? "***" : "<unset>") + "]";
		}

	}

	/**
	 * @param checkoutBaseUrl Core address seen by the browser, for the simulated payment page
	 * @param webhookUrl where the simulator delivers the confirmation (the Core's own Asaas webhook)
	 */
	public record Fake(String checkoutBaseUrl, String webhookUrl) {
	}

}
