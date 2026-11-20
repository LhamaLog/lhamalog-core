package br.com.lhamalog.core.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import br.com.lhamalog.core.billing.application.port.out.PaymentGateway;
import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.domain.GatewayCharge;
import br.com.lhamalog.core.billing.domain.Plan;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.identity.domain.OrganizationSession;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.FieldValidationException;

/**
 * Plans, purchase orders and the latest order of an organization (spec 010, US1). Only an administrator of an
 * organization without tenant can buy; a newer order cancels the pending ones (FR-004).
 */
public class CheckoutService {

	static final String NOT_ADMIN_CODE = "not_admin";

	static final String ALREADY_SUBSCRIBED_CODE = "already_subscribed";

	static final String INVALID_PLAN_CODE = "invalid_plan";

	static final String NO_ORDER_CODE = "no_order";

	static final int COMPANY_NAME_MIN = 2;

	static final int COMPANY_NAME_MAX = 120;

	private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

	private final PlanCatalog catalog;

	private final SubscriptionOrderRepository orders;

	private final PaymentGateway gateway;

	private final Clock clock;

	private final Supplier<String> idGenerator;

	public CheckoutService(PlanCatalog catalog, SubscriptionOrderRepository orders, PaymentGateway gateway,
			Clock clock) {
		this(catalog, orders, gateway, clock, () -> UUID.randomUUID().toString());
	}

	CheckoutService(PlanCatalog catalog, SubscriptionOrderRepository orders, PaymentGateway gateway, Clock clock,
			Supplier<String> idGenerator) {
		this.catalog = catalog;
		this.orders = orders;
		this.gateway = gateway;
		this.clock = clock;
		this.idGenerator = idGenerator;
	}

	public List<Plan> plans() {
		return catalog.plans();
	}

	/**
	 * Creates a PENDING order and its charge at the gateway.
	 * @return the order with the charge id and the payment page
	 */
	public SubscriptionOrder checkout(OrganizationSession session, String planName, String companyName) {
		if (!session.administrator()) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, "Somente o administrador da organização pode contratar"
					+ " um plano.", NOT_ADMIN_CODE);
		}
		if (session.tenant().isPresent()) {
			throw new ApiStatusException(HttpStatus.CONFLICT, "Sua empresa já tem um plano ativo.",
					ALREADY_SUBSCRIBED_CODE);
		}
		Plan plan = catalog.find(planName)
			.orElseThrow(() -> new ApiStatusException(HttpStatus.BAD_REQUEST, "Escolha um plano disponível.",
					INVALID_PLAN_CODE));
		String name = validCompanyName(companyName);
		Instant now = clock.instant();
		SubscriptionOrder order = SubscriptionOrder.pending(idGenerator.get(), session.clerkOrgId(),
				session.clerkUserId(), name, plan, now);
		// The charge comes first: if the gateway is unavailable, no order is left behind and no pending one is lost.
		GatewayCharge charge = gateway.createCharge(order);
		SubscriptionOrder charged = order.withCharge(charge);
		orders.replacePending(charged, now);
		log.info("event=billing.checkout.created | Subscription order created | orderId={} clerkOrgId={} plan={}",
				charged.orderId(), charged.clerkOrgId(), charged.planTier());
		return charged;
	}

	/** Latest order of the organization and whether it already has an active tenant. */
	public LatestOrder latest(OrganizationSession session) {
		SubscriptionOrder order = orders.findLatestByClerkOrgId(session.clerkOrgId())
			.orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "Nenhum pedido de assinatura encontrado.",
					NO_ORDER_CODE));
		return new LatestOrder(order, session.tenant().isPresent());
	}

	private static String validCompanyName(String companyName) {
		String name = (companyName != null) ? companyName.strip() : "";
		if (name.length() < COMPANY_NAME_MIN || name.length() > COMPANY_NAME_MAX) {
			throw new FieldValidationException(
					Map.of("companyName", "Informe o nome da empresa, com 2 a 120 caracteres."));
		}
		return name;
	}

	/**
	 * @param subscriptionActive whether the organization already has an active tenant (the Web stops waiting)
	 */
	public record LatestOrder(SubscriptionOrder order, boolean subscriptionActive) {
	}

}
