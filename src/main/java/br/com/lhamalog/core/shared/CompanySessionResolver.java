package br.com.lhamalog.core.shared;

import org.springframework.stereotype.Component;

import br.com.lhamalog.core.identity.application.port.CurrentRequestContext;

/** Gives controllers the {@link CompanySession} of the current request. */
@Component
public class CompanySessionResolver {

	private final CurrentRequestContext currentRequestContext;

	public CompanySessionResolver(CurrentRequestContext currentRequestContext) {
		this.currentRequestContext = currentRequestContext;
	}

	public CompanySession current() {
		return CompanySession.from(currentRequestContext.current());
	}

}
