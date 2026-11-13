package br.com.lhamalog.core.identity.application.port;

import br.com.lhamalog.core.identity.domain.RequestContext;

/** Port through which the rest of the Core reads the identity of the current request. */
public interface CurrentRequestContext {

	/**
	 * @return the context of the current authenticated request
	 * @throws IllegalStateException when called outside an authenticated request
	 */
	RequestContext current();

}
