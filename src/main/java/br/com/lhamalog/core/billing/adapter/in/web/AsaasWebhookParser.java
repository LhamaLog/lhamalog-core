package br.com.lhamalog.core.billing.adapter.in.web;

import java.math.BigDecimal;

import br.com.lhamalog.core.billing.domain.GatewayNotification;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads an Asaas webhook body (research R1): {@code id}, {@code event} and, for charge events, the {@code payment}
 * object with {@code id}, {@code value} and {@code externalReference}. Unknown fields are ignored, since Asaas adds new
 * ones over time.
 */
public final class AsaasWebhookParser {

	public static final String GATEWAY = "ASAAS";

	/** The body is not a well-formed Asaas event. */
	public static final class InvalidAsaasPayloadException extends RuntimeException {

		InvalidAsaasPayloadException(String message) {
			super(message);
		}

	}

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public GatewayNotification parse(byte[] body) {
		if (body == null || body.length == 0) {
			throw new InvalidAsaasPayloadException("empty body");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(body);
		}
		catch (JacksonException ex) {
			throw new InvalidAsaasPayloadException("malformed JSON");
		}
		if (root == null || !root.isObject()) {
			throw new InvalidAsaasPayloadException("body is not a JSON object");
		}
		String eventId = text(root, "id");
		String type = text(root, "event");
		if (eventId == null || type == null) {
			throw new InvalidAsaasPayloadException("missing id or event");
		}
		JsonNode payment = root.get("payment");
		String chargeId = null;
		String orderId = null;
		BigDecimal value = null;
		if (payment != null && payment.isObject()) {
			chargeId = text(payment, "id");
			orderId = text(payment, "externalReference");
			JsonNode amount = payment.get("value");
			value = (amount != null && amount.isNumber()) ? amount.decimalValue() : null;
		}
		return new GatewayNotification(eventId, GATEWAY, type, orderId, chargeId, value);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isString()) {
			return null;
		}
		String text = value.asString().strip();
		return text.isEmpty() ? null : text;
	}

}
