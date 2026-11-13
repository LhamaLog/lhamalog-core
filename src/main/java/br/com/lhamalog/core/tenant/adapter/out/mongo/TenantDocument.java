package br.com.lhamalog.core.tenant.adapter.out.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB representation of a tenant. The tenantId is stored as {@code _id}. */
@Document(TenantDocument.COLLECTION)
public record TenantDocument(@Id String tenantId, String clerkOrgId, String name, String status) {

	public static final String COLLECTION = "tenant";

	public static final String CLERK_ORG_ID = "clerkOrgId";

}
