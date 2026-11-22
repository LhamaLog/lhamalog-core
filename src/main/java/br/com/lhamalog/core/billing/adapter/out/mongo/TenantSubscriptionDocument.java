package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB representation of a tenant subscription. The tenantId is the {@code _id}: one per tenant. */
@Document(TenantSubscriptionDocument.COLLECTION)
public record TenantSubscriptionDocument(@Id String tenantId, String planTier, String status, String orderId,
		Instant startedAt) {

	public static final String COLLECTION = "tenant_subscription";

}
