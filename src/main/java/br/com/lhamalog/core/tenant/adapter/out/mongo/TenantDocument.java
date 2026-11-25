package br.com.lhamalog.core.tenant.adapter.out.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB representation of a tenant. The tenantId is stored as {@code _id}. {@code originOrderId} is the purchase
 * order that created the tenant (spec 010); it is absent for tenants created elsewhere (development seed, tests).
 */
@Document(TenantDocument.COLLECTION)
public record TenantDocument(@Id String tenantId, String clerkOrgId, String name, String status,
		String originOrderId) {

	public static final String COLLECTION = "tenant";

	public static final String CLERK_ORG_ID = "clerkOrgId";

	public static final String ORIGIN_ORDER_ID = "originOrderId";

	@PersistenceCreator
	public TenantDocument {
	}

	/** Tenant not created by a purchase. */
	public TenantDocument(String tenantId, String clerkOrgId, String name, String status) {
		this(tenantId, clerkOrgId, name, status, null);
	}

}
