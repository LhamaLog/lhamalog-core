package br.com.lhamalog.core.tenant.application.port.out;

import java.util.Optional;

import br.com.lhamalog.core.tenant.domain.Tenant;

/** Output port to read tenants. */
public interface TenantRepository {

	/**
	 * Finds the tenant mirrored by a Clerk Organization. This is the only tenant-agnostic lookup, because it is how
	 * the tenant of a request is discovered.
	 */
	Optional<Tenant> findByClerkOrgId(String clerkOrgId);

}
