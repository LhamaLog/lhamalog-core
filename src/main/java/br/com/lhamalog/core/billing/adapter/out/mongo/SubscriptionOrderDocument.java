package br.com.lhamalog.core.billing.adapter.out.mongo;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** MongoDB representation of a subscription order. The orderId is stored as {@code _id}. */
@Document(SubscriptionOrderDocument.COLLECTION)
public record SubscriptionOrderDocument(@Id String orderId, String clerkOrgId, String clerkUserId, String companyName,
		String planTier, @Field(targetType = FieldType.DECIMAL128) BigDecimal price, String status,
		String gatewayChargeId, String paymentUrl, Instant createdAt, Instant paidAt, Instant canceledAt) {

	public static final String COLLECTION = "subscription_order";

	static final String CLERK_ORG_ID = "clerkOrgId";

	static final String CREATED_AT = "createdAt";

	static final String STATUS = "status";

	static final String GATEWAY_CHARGE_ID = "gatewayChargeId";

}
