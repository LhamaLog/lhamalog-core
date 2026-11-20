package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB representation of a gateway event. The gateway event id is the {@code _id}; no TTL (ADR_125).
 * {@code claimedAt} is when the current processing started; a RECEIVED event older than the lease is taken again.
 */
@Document(GatewayEventDocument.COLLECTION)
public record GatewayEventDocument(@Id String eventId, String gateway, String type, String orderId, String chargeId,
		String status, String reason, String tenantId, int attempts, Instant receivedAt, Instant processedAt,
		Instant claimedAt) {

	public static final String COLLECTION = "gateway_event";

	static final String STATUS = "status";

	static final String CLAIMED_AT = "claimedAt";

	static final String RECEIVED_AT = "receivedAt";

}
