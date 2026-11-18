package br.com.lhamalog.core.billing.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A notification received from the payment gateway, recorded once per gateway event id (EventoGateway, ADR_125/133).
 * It keeps what a future internal reprocessing route needs: status, reason and attempts.
 *
 * @param reason why it was ignored or failed; {@code null} when executed
 * @param attempts how many times it was processed
 */
public record GatewayEvent(String eventId, String gateway, String type, String orderId, String chargeId,
		GatewayEventStatus status, String reason, String tenantId, int attempts, Instant receivedAt,
		Instant processedAt) {

	public GatewayEvent {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(gateway, "gateway");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(receivedAt, "receivedAt");
	}

	public static GatewayEvent received(GatewayNotification notification, Instant now) {
		return new GatewayEvent(notification.eventId(), notification.gateway(), notification.type(),
				notification.orderId(), notification.chargeId(), GatewayEventStatus.RECEIVED, null, null, 1, now, null);
	}

}
