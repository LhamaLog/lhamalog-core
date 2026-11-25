package br.com.lhamalog.core.billing.domain;

import java.util.Objects;

/**
 * Charge created at the payment gateway for an order.
 *
 * @param chargeId gateway id of the charge ({@code pay_...} at Asaas)
 * @param paymentUrl page where the buyer pays
 */
public record GatewayCharge(String chargeId, String paymentUrl) {

	public GatewayCharge {
		Objects.requireNonNull(chargeId, "chargeId");
		Objects.requireNonNull(paymentUrl, "paymentUrl");
	}

}
