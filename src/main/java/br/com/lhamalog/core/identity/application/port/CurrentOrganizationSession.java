package br.com.lhamalog.core.identity.application.port;

import br.com.lhamalog.core.identity.domain.OrganizationSession;

/** Port through which the purchase routes read the session of the current request, with or without a tenant. */
public interface CurrentOrganizationSession {

	/**
	 * @return the session of the current request
	 * @throws IllegalStateException outside a request granted by the purchase access rule
	 */
	OrganizationSession current();

}
