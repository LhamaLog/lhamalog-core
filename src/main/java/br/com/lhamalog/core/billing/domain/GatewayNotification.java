package br.com.lhamalog.core.billing.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * An authentic gateway event already read from its payload, free of the gateway JSON format.
 *
 * @param eventId gateway event id, the idempotency key
 * @param gateway gateway name (for example {@code ASAAS})
 * @param type gateway event type (for example {@code PAYMENT_RECEIVED})
 * @param orderId our order id, sent as the charge {@code externalReference}; may be {@code null}
 * @param chargeId gateway charge id; may be {@code null}
 * @param value charged value; may be {@code null}
 */
public record GatewayNotification(String eventId, String gateway, String type, String orderId, String chargeId,
		BigDecimal value) {

	/** Asaas events that mean the payment happened: card confirmation or money received (research R2). */
	static final Set<String> PAYMENT_CONFIRMATIONS = Set.of("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED");

	public GatewayNotification {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(gateway, "gateway");
		Objects.requireNonNull(type, "type");
	}

	public boolean confirmsPayment() {
		return PAYMENT_CONFIRMATIONS.contains(type);
	}

}
