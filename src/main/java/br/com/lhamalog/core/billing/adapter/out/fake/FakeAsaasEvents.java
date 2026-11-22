package br.com.lhamalog.core.billing.adapter.out.fake;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Builds simulated Asaas events in the documented webhook format (research R1). */
public final class FakeAsaasEvents {

	static final String EVENT_PREFIX = "evt_fake_";

	/** Asaas sends {@code dateCreated} as {@code yyyy-MM-dd HH:mm:ss} in Brasília time. */
	private static final DateTimeFormatter DATE_CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneId.of("America/Sao_Paulo"));

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private FakeAsaasEvents() {
	}

	/** A {@code PAYMENT_RECEIVED} (Pix) for the charge of the order, with a new event id. */
	public static String paymentReceived(SubscriptionOrder order, Instant now) {
		ObjectNode root = JSON.createObjectNode();
		root.put("id", EVENT_PREFIX + UUID.randomUUID().toString().replace("-", ""));
		root.put("event", "PAYMENT_RECEIVED");
		root.put("dateCreated", DATE_CREATED.format(now));
		ObjectNode payment = root.putObject("payment");
		payment.put("object", "payment");
		payment.put("id", order.gatewayChargeId());
		payment.put("customer", "cus_fake_" + order.clerkOrgId());
		payment.put("value", order.price());
		payment.put("status", "RECEIVED");
		payment.put("billingType", "PIX");
		payment.put("externalReference", order.orderId());
		return JSON.writeValueAsString(root);
	}

}
