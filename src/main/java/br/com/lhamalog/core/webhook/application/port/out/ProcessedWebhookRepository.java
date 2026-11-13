package br.com.lhamalog.core.webhook.application.port.out;

import java.time.Instant;

/** Output port that remembers processed Svix message ids (deduplication). */
public interface ProcessedWebhookRepository {

	boolean isProcessed(String svixId);

	/**
	 * Records a processed message. Idempotent: recording the same id twice is not an error.
	 * @param tenantId tenant of the event, or {@code null} when the event is not tied to one tenant
	 */
	void markProcessed(String svixId, String type, String tenantId, Instant processedAt);

}
