package br.com.lhamalog.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Clerk webhook settings (LLT-164).
 *
 * @param signingSecret Svix signing secret of the endpoint ({@code whsec_...}); when blank every webhook is rejected
 * @param tolerance accepted distance between {@code svix-timestamp} and the server clock
 */
@ConfigurationProperties("clerk.webhook")
public record ClerkWebhookProperties(String signingSecret, @DefaultValue("5m") Duration tolerance) {

	@Override
	public String toString() {
		// Never print the secret.
		boolean configured = signingSecret != null && !signingSecret.isBlank();
		return "ClerkWebhookProperties[signingSecret=" + (configured ? "***" : "<unset>") + ", tolerance=" + tolerance
				+ "]";
	}

}
