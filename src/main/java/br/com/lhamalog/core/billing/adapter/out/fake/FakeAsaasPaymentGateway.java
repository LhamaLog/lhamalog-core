package br.com.lhamalog.core.billing.adapter.out.fake;

import java.util.UUID;

import br.com.lhamalog.core.billing.application.port.out.PaymentGateway;
import br.com.lhamalog.core.billing.domain.GatewayCharge;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;

/**
 * Asaas simulator, local profile only (spec 010, research R9). Creates a fake charge whose payment page is served by
 * {@link FakeAsaasCheckoutController}; nothing leaves the machine.
 */
public final class FakeAsaasPaymentGateway implements PaymentGateway {

	static final String CHARGE_PREFIX = "pay_fake_";

	private final String checkoutBaseUrl;

	public FakeAsaasPaymentGateway(String checkoutBaseUrl) {
		this.checkoutBaseUrl = stripTrailingSlash(checkoutBaseUrl);
	}

	@Override
	public GatewayCharge createCharge(SubscriptionOrder order) {
		String chargeId = CHARGE_PREFIX + UUID.randomUUID().toString().replace("-", "");
		return new GatewayCharge(chargeId, checkoutBaseUrl + FakeAsaasCheckoutController.PATH + "/" + chargeId);
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
