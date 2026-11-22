package br.com.lhamalog.core.billing.adapter.out;

import org.springframework.http.HttpStatus;

import br.com.lhamalog.core.billing.application.port.out.PaymentGateway;
import br.com.lhamalog.core.billing.domain.GatewayCharge;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.shared.ApiStatusException;

/**
 * Gateway outside the local profile until the real Asaas adapter exists (spec 010, assumptions): every purchase is
 * answered with 503 {@code payment_unavailable}, and no order is created.
 */
public final class UnavailablePaymentGateway implements PaymentGateway {

	static final String CODE = "payment_unavailable";

	@Override
	public GatewayCharge createCharge(SubscriptionOrder order) {
		throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"A contratação de planos ainda não está disponível. Tente novamente mais tarde.", CODE);
	}

}
