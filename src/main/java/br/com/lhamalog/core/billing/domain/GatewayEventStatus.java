package br.com.lhamalog.core.billing.domain;

/**
 * Processing state of a gateway event (EventoGateway, ADR_125). EXECUTED and IGNORED are final; a FAILED event is
 * processed again when the gateway resends it.
 */
public enum GatewayEventStatus {

	RECEIVED,

	EXECUTED,

	IGNORED,

	FAILED;

	public boolean isFinal() {
		return this == EXECUTED || this == IGNORED;
	}

}
