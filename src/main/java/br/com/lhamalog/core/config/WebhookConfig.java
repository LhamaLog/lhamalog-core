package br.com.lhamalog.core.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.lhamalog.core.member.application.MemberSynchronizer;
import br.com.lhamalog.core.member.application.port.out.DeletedUserRepository;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.tenant.application.port.out.TenantRepository;
import br.com.lhamalog.core.webhook.adapter.in.web.ClerkWebhookEventParser;
import br.com.lhamalog.core.webhook.adapter.in.web.SvixSignatureVerifier;
import br.com.lhamalog.core.webhook.application.ClerkWebhookProcessor;
import br.com.lhamalog.core.webhook.application.port.out.ProcessedWebhookRepository;

/** Wires the Clerk webhook (LLT-164). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClerkWebhookProperties.class)
public class WebhookConfig {

	@Bean
	SvixSignatureVerifier svixSignatureVerifier(ClerkWebhookProperties properties) {
		Duration tolerance = (properties.tolerance() != null) ? properties.tolerance() : Duration.ofMinutes(5);
		return new SvixSignatureVerifier(properties.signingSecret(), tolerance, Clock.systemUTC());
	}

	@Bean
	ClerkWebhookEventParser clerkWebhookEventParser() {
		return new ClerkWebhookEventParser();
	}

	@Bean
	MemberSynchronizer memberSynchronizer(TenantRepository tenants, MemberRepository members,
			DeletedUserRepository deletedUsers) {
		return new MemberSynchronizer(tenants, members, deletedUsers);
	}

	@Bean
	ClerkWebhookProcessor clerkWebhookProcessor(ProcessedWebhookRepository processedWebhooks,
			MemberSynchronizer memberSynchronizer) {
		return new ClerkWebhookProcessor(processedWebhooks, memberSynchronizer, Clock.systemUTC());
	}

}
