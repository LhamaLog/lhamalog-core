package br.com.lhamalog.core.webhook.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Processed Svix message. Expires through a TTL index on {@code processedAt}. */
@Document(ProcessedWebhookDocument.COLLECTION)
public record ProcessedWebhookDocument(@Id String svixId, String type, String tenantId, Instant processedAt) {

	public static final String COLLECTION = "clerk_webhook_event";

	static final String PROCESSED_AT = "processedAt";

}
