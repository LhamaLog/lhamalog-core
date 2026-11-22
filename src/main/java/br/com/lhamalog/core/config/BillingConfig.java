package br.com.lhamalog.core.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookParser;
import br.com.lhamalog.core.billing.adapter.out.UnavailablePaymentGateway;
import br.com.lhamalog.core.billing.adapter.out.fake.AsaasWebhookSender;
import br.com.lhamalog.core.billing.adapter.out.fake.FakeAsaasPaymentGateway;
import br.com.lhamalog.core.billing.application.CheckoutService;
import br.com.lhamalog.core.billing.application.GatewayEventProcessor;
import br.com.lhamalog.core.billing.application.PlanCatalog;
import br.com.lhamalog.core.billing.application.SubscriptionActivationService;
import br.com.lhamalog.core.billing.application.port.out.GatewayEventRepository;
import br.com.lhamalog.core.billing.application.port.out.PaymentGateway;
import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.application.port.out.TenantSubscriptionRepository;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning;

/**
 * Wires the purchase flow (spec 010). The Asaas simulator and a generated webhook token exist only in the local
 * profile; elsewhere the token is mandatory and purchases answer 503 until the real Asaas adapter exists.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingProperties.class)
public class BillingConfig {

	@Bean
	PlanCatalog planCatalog(BillingProperties properties) {
		return new PlanCatalog(properties.prices());
	}

	@Bean
	CheckoutService checkoutService(PlanCatalog catalog, SubscriptionOrderRepository orders, PaymentGateway gateway,
			Clock clock) {
		return new CheckoutService(catalog, orders, gateway, clock);
	}

	@Bean
	SubscriptionActivationService subscriptionActivationService(SubscriptionOrderRepository orders,
			TenantProvisioning tenants, TenantSubscriptionRepository subscriptions, MemberRepository members,
			Clock clock) {
		return new SubscriptionActivationService(orders, tenants, subscriptions, members, clock);
	}

	@Bean
	GatewayEventProcessor gatewayEventProcessor(GatewayEventRepository events,
			SubscriptionActivationService activation, Clock clock) {
		return new GatewayEventProcessor(events, activation, clock);
	}

	@Bean
	AsaasWebhookParser asaasWebhookParser() {
		return new AsaasWebhookParser();
	}

	@Bean
	@Profile("!local")
	AsaasWebhookCredential asaasWebhookCredential(BillingProperties properties) {
		return AsaasWebhookCredential.configured(properties.asaas().webhookToken());
	}

	@Bean
	@Profile("!local")
	PaymentGateway unavailablePaymentGateway() {
		return new UnavailablePaymentGateway();
	}

	/** Asaas simulator (research R9). */
	@Configuration(proxyBeanMethods = false)
	@Profile("local")
	static class FakeAsaasConfig {

		@Bean
		AsaasWebhookCredential localAsaasWebhookCredential(BillingProperties properties) {
			return AsaasWebhookCredential.generatedWhenBlank(properties.asaas().webhookToken());
		}

		@Bean
		PaymentGateway fakeAsaasPaymentGateway(BillingProperties properties) {
			return new FakeAsaasPaymentGateway(properties.fake().checkoutBaseUrl());
		}

		/** Plain {@code RestClient.builder()}, as in IdentityConfig: no new dependency. */
		@Bean
		AsaasWebhookSender asaasWebhookSender(BillingProperties properties, AsaasWebhookCredential credential) {
			return new AsaasWebhookSender(RestClient.builder(), properties.fake().webhookUrl(), credential);
		}

	}

}
