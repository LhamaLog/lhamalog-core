package br.com.lhamalog.core.billing.adapter.out.fake;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.HtmlUtils;

import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.config.BillingProperties;

/**
 * Payment page of the Asaas simulator (spec 010, FR-013). Exists only in the local profile; elsewhere its paths answer
 * 404. Public like the real Asaas checkout: the browser reaches it without the Clerk token, and the charge id is the
 * only key. "Simular pagamento" sends a {@code PAYMENT_RECEIVED} to the real webhook through
 * {@link AsaasWebhookSender}.
 */
@Controller
@Profile("local")
@RequestMapping(FakeAsaasCheckoutController.PATH)
public class FakeAsaasCheckoutController {

	public static final String PATH = "/dev/fake-asaas/checkouts";

	private static final Logger log = LoggerFactory.getLogger(FakeAsaasCheckoutController.class);

	private final SubscriptionOrderRepository orders;

	private final AsaasWebhookSender sender;

	private final Clock clock;

	private final String returnUrl;

	public FakeAsaasCheckoutController(SubscriptionOrderRepository orders, AsaasWebhookSender sender, Clock clock,
			BillingProperties properties) {
		this.orders = orders;
		this.sender = sender;
		this.clock = clock;
		this.returnUrl = properties.returnUrl();
	}

	@GetMapping("/{chargeId}")
	ResponseEntity<String> page(@PathVariable String chargeId) {
		Optional<SubscriptionOrder> order = orders.findByGatewayChargeId(chargeId);
		if (order.isEmpty()) {
			return html(HttpStatus.NOT_FOUND, notFound());
		}
		SubscriptionOrder found = order.get();
		String body = """
				<p>Empresa: <strong>%s</strong></p>
				<p>Plano: <strong>%s</strong> &middot; %s por m&ecirc;s</p>
				<form method="post" action="%s/%s/pay">
				  <button type="submit">Simular pagamento</button>
				</form>
				<p class="note">Nenhum valor &eacute; cobrado: este &eacute; o simulador local do Asaas.</p>
				""".formatted(escape(found.companyName()), escape(found.planTier().name()), money(found),
				PATH, escape(chargeId));
		return html(HttpStatus.OK, page("Pagamento simulado", body));
	}

	@PostMapping("/{chargeId}/pay")
	ResponseEntity<String> pay(@PathVariable String chargeId) {
		Optional<SubscriptionOrder> order = orders.findByGatewayChargeId(chargeId);
		if (order.isEmpty()) {
			return html(HttpStatus.NOT_FOUND, notFound());
		}
		int status = sender.send(FakeAsaasEvents.paymentReceived(order.get(), clock.instant()));
		log.info("event=billing.fake.payment_sent | Simulated Asaas payment delivered to the webhook | orderId={}"
				+ " chargeId={} webhookStatus={}", order.get().orderId(), chargeId, status);
		boolean delivered = status >= 200 && status < 300;
		String body = delivered
				? "<p>Pagamento confirmado. O acesso da empresa foi liberado.</p>"
				: "<p>O Core n&atilde;o aceitou a confirma&ccedil;&atilde;o (status " + status
						+ "). Veja o log do Core e tente de novo.</p>";
		body += "<p><a href=\"" + escape(returnUrl) + "\">Voltar ao LhamaLog</a></p>";
		return html(delivered ? HttpStatus.OK : HttpStatus.BAD_GATEWAY,
				page(delivered ? "Pagamento confirmado" : "Falha na confirma&ccedil;&atilde;o", body));
	}

	private static String notFound() {
		return page("Cobran&ccedil;a n&atilde;o encontrada", "<p>Esta cobran&ccedil;a simulada n&atilde;o existe.</p>");
	}

	private static String page(String title, String body) {
		return """
				<!doctype html>
				<html lang="pt-BR">
				<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
				<title>%s</title>
				<style>body{font-family:system-ui,sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem}
				button{font-size:1rem;padding:.6rem 1.2rem;cursor:pointer}.note{color:#666;font-size:.9rem}</style>
				</head>
				<body><h1>%s</h1>%s</body>
				</html>
				""".formatted(title, title, body);
	}

	private static String money(SubscriptionOrder order) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(order.price());
	}

	private static String escape(String value) {
		return HtmlUtils.htmlEscape(value);
	}

	private static ResponseEntity<String> html(HttpStatus status, String body) {
		return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)).body(body);
	}

}
