package br.com.lhamalog.core.billing.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.billing.application.CheckoutService;
import br.com.lhamalog.core.billing.application.CheckoutService.LatestOrder;
import br.com.lhamalog.core.billing.domain.Plan;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.identity.application.port.CurrentOrganizationSession;

/**
 * Purchase routes (spec 010, contracts/api.md). They also serve organizations without tenant: the security chain lets
 * those sessions in only here.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

	static final String CURRENCY = "BRL";

	private final CheckoutService checkout;

	private final CurrentOrganizationSession sessions;

	public BillingController(CheckoutService checkout, CurrentOrganizationSession sessions) {
		this.checkout = checkout;
		this.sessions = sessions;
	}

	@GetMapping("/plans")
	List<PlanView> plans() {
		return checkout.plans().stream().map(BillingController::view).toList();
	}

	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	CheckoutView checkout(@RequestBody CheckoutRequest request) {
		SubscriptionOrder order = checkout.checkout(sessions.current(), request.plan(), request.companyName());
		return new CheckoutView(order.orderId(), order.planTier().name(), order.status().name(), order.paymentUrl());
	}

	@GetMapping("/order")
	OrderView order() {
		LatestOrder latest = checkout.latest(sessions.current());
		SubscriptionOrder order = latest.order();
		return new OrderView(order.orderId(), order.planTier().name(), order.status().name(), order.paymentUrl(),
				latest.subscriptionActive());
	}

	private static PlanView view(Plan plan) {
		return new PlanView(plan.tier().name(), plan.name(), plan.price(), CURRENCY);
	}

	record CheckoutRequest(String plan, String companyName) {
	}

	record PlanView(String plan, String name, BigDecimal price, String currency) {
	}

	record CheckoutView(String orderId, String plan, String status, String paymentUrl) {
	}

	record OrderView(String orderId, String plan, String status, String paymentUrl, boolean subscriptionActive) {
	}

}
