package br.com.lhamalog.core.finderalert;

import java.util.Set;

import org.springframework.http.HttpStatus;

import br.com.lhamalog.core.occurrence.application.InternalApiException;
import tools.jackson.databind.JsonNode;

/**
 * Strict reader of {@code POST /internal/v1/alertas/finder}. Unknown fields, a missing field or a reason outside the
 * list are 422 {@code DADOS_INVALIDOS}. A body that is not a JSON object is 400. Nothing read here is logged.
 */
public final class FinderAlertRequests {

	private static final Set<String> FIELDS = Set.of("tenantId", "productId", "batchId", "motivo");

	private FinderAlertRequests() {
	}

	public static FinderAlertCommand read(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new InternalApiException(HttpStatus.BAD_REQUEST, InternalApiException.INVALID_REQUEST);
		}
		for (String name : body.propertyNames()) {
			if (!FIELDS.contains(name)) throw InternalApiException.invalidData();
		}
		for (String name : FIELDS) {
			if (!body.has(name)) throw InternalApiException.invalidData();
		}
		String motivo = text(body.get("motivo"));
		if (!FinderAlertCommand.OPENOBSERVE_UNAVAILABLE.equals(motivo)) throw InternalApiException.invalidData();
		return new FinderAlertCommand(text(body.get("tenantId")), text(body.get("productId")), text(body.get("batchId")),
				motivo);
	}

	private static String text(JsonNode node) {
		if (node == null || !node.isString() || node.stringValue().isBlank()) throw InternalApiException.invalidData();
		return node.stringValue();
	}

}
