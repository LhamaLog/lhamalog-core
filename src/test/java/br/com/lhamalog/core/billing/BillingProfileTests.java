package br.com.lhamalog.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.out.UnavailablePaymentGateway;
import br.com.lhamalog.core.billing.adapter.out.fake.AsaasWebhookSender;
import br.com.lhamalog.core.billing.adapter.out.fake.FakeAsaasCheckoutController;
import br.com.lhamalog.core.billing.adapter.out.fake.FakeAsaasPaymentGateway;
import br.com.lhamalog.core.billing.application.port.out.GatewayEventRepository;
import br.com.lhamalog.core.billing.application.port.out.PaymentGateway;
import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.application.port.out.TenantSubscriptionRepository;
import br.com.lhamalog.core.config.BillingConfig;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning;

/** Spec 010, US4: the simulator exists only in the local profile; elsewhere the webhook token is mandatory. */
class BillingProfileTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(BillingConfig.class)
		.withBean(SubscriptionOrderRepository.class, () -> mock(SubscriptionOrderRepository.class))
		.withBean(GatewayEventRepository.class, () -> mock(GatewayEventRepository.class))
		.withBean(TenantSubscriptionRepository.class, () -> mock(TenantSubscriptionRepository.class))
		.withBean(TenantProvisioning.class, () -> mock(TenantProvisioning.class))
		.withBean(MemberRepository.class, () -> mock(MemberRepository.class))
		.withBean(Clock.class, Clock::systemUTC);

	@Test
	void prodHasNoSimulatorAndAnswersPurchasesAsUnavailable() {
		runner.withInitializer((context) -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues("lhamalog.billing.asaas.webhook-token=" + "p".repeat(40))
			.run((context) -> {
				assertThat(context).hasNotFailed();
				assertThat(context).getBean(PaymentGateway.class).isInstanceOf(UnavailablePaymentGateway.class);
				assertThat(context).doesNotHaveBean(FakeAsaasPaymentGateway.class);
				assertThat(context).doesNotHaveBean(AsaasWebhookSender.class);
				assertThat(context.getBean(AsaasWebhookCredential.class).matches("p".repeat(40))).isTrue();
			});
	}

	@Test
	void prodWithoutWebhookTokenDoesNotStart() {
		runner.withInitializer((context) -> context.getEnvironment().setActiveProfiles("prod"))
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ASAAS_WEBHOOK_TOKEN"));
	}

	@Test
	void localHasTheSimulatorWithAGeneratedToken() {
		runner.withInitializer((context) -> context.getEnvironment().setActiveProfiles("local"))
			.withPropertyValues("lhamalog.billing.fake.checkout-base-url=http://localhost:8080",
					"lhamalog.billing.fake.webhook-url=http://localhost:8080/webhooks/asaas")
			.run((context) -> {
				assertThat(context).hasNotFailed();
				assertThat(context).getBean(PaymentGateway.class).isInstanceOf(FakeAsaasPaymentGateway.class);
				assertThat(context).hasSingleBean(AsaasWebhookSender.class);
				assertThat(context.getBean(AsaasWebhookCredential.class).value()).hasSizeGreaterThanOrEqualTo(32);
			});
	}

	@Test
	void simulatorPageIsOnlyRegisteredInTheLocalProfile() {
		assertThat(FakeAsaasCheckoutController.class.getAnnotation(Profile.class).value()).containsExactly("local");
	}

}
