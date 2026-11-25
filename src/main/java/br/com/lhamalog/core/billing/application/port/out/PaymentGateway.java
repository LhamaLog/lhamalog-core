package br.com.lhamalog.core.billing.application.port.out;

import br.com.lhamalog.core.billing.domain.GatewayCharge;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;

/**
 * Provider-independent payment gateway (LLT-235). The Asaas simulator implements it in the local profile; the real
 * Asaas adapter comes in its own feature. Confirmations come back through the gateway webhook, never through this
 * port.
 */
public interface PaymentGateway {

	/**
	 * Creates the charge of the order, carrying {@link SubscriptionOrder#orderId()} as the external reference.
	 * @return the charge id and the page where the buyer pays
	 */
	GatewayCharge createCharge(SubscriptionOrder order);

}
