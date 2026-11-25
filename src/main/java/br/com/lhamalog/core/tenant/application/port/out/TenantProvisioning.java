package br.com.lhamalog.core.tenant.application.port.out;

import java.util.Objects;

/** Output port that creates the tenant of a purchase (ADR_121, spec 010). */
public interface TenantProvisioning {

	/**
	 * Makes sure the organization has a tenant. Creates an ACTIVE one marked with {@code orderId} when missing and never
	 * changes an existing tenant; at most one tenant per organization, even under concurrent calls.
	 * @return the tenant of the organization and the order that created it ({@code null} when it was created elsewhere,
	 * for example by the local development seed)
	 */
	ProvisionedTenant provisionForOrder(String clerkOrgId, String name, String orderId);

	/**
	 * @param tenantId internal tenant id
	 * @param originOrderId order that created the tenant; {@code null} when it was not created by a purchase
	 * @param created whether this call created it
	 */
	record ProvisionedTenant(String tenantId, String originOrderId, boolean created) {

		public ProvisionedTenant {
			Objects.requireNonNull(tenantId, "tenantId");
		}

		public boolean createdBy(String orderId) {
			return orderId.equals(originOrderId);
		}

	}

}
