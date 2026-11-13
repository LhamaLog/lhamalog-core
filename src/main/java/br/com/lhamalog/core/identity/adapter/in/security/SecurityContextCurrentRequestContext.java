package br.com.lhamalog.core.identity.adapter.in.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.lhamalog.core.identity.application.port.CurrentRequestContext;
import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.identity.domain.RequestContext;

/** Reads the {@link RequestContext} from Spring Security's context of the current request. */
public final class SecurityContextCurrentRequestContext implements CurrentRequestContext {

	@Override
	public RequestContext current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof ClerkAuthentication clerk
				&& clerk.decision() instanceof AccessDecision.Granted granted) {
			return granted.context();
		}
		throw new IllegalStateException("No authenticated request context");
	}

}
