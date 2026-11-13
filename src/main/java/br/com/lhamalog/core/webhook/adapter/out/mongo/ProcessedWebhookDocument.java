package br.com.lhamalog.core.webhook.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Processed Svix message. Expires through a TTL index on {@code processedAt}. {@code tenantId} is written even when
 * null, so events that belong to no tenant carry an explicit null (spec 003, FR-011).
 */
@Document(ProcessedWebhookDocument.COLLECTION)
public record ProcessedWebhookDocument(@Id String svixId, String type, @Field(write = Field.Write.ALWAYS) String tenantId, Instant processedAt) {

	public static final String COLLECTION = "clerk_webhook_event";

	static final String PROCESSED_AT = "processedAt";

}
