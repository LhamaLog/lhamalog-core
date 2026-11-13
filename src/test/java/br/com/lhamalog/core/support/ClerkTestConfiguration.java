package br.com.lhamalog.core.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Shared test wiring for the Clerk integration: JWKS of {@link TestJwks}, the revocation fake and the protected test
 * route. Every Spring test imports it so that they all share one application context (and one set of containers).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(ProtectedTestController.class)
public class ClerkTestConfiguration {

	@Bean
	DynamicPropertyRegistrar clerkJwksProperties() {
		return (registry) -> registry.add("clerk.jwks-uri", TestJwks::jwksUri);
	}

	/** Random in-memory webhook secret; overrides the empty value of the local profile. */
	@Bean
	DynamicPropertyRegistrar clerkWebhookProperties() {
		return (registry) -> registry.add("clerk.webhook.signing-secret", () -> TestSvix.SECRET);
	}

	@Bean
	@Primary
	RecordingSessionRevocation recordingSessionRevocation() {
		return new RecordingSessionRevocation();
	}

}
