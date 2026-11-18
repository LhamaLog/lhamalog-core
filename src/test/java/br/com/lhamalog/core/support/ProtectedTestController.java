package br.com.lhamalog.core.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.identity.application.port.CurrentRequestContext;
import br.com.lhamalog.core.identity.domain.RequestContext;

/** Protected route that only exists in tests: returns the context exposed by the port. */
@RestController
public class ProtectedTestController {

	public static final String PATH = "/test/context";

	private final CurrentRequestContext currentRequestContext;

	public ProtectedTestController(CurrentRequestContext currentRequestContext) {
		this.currentRequestContext = currentRequestContext;
	}

	@GetMapping(PATH)
	RequestContext context() {
		return currentRequestContext.current();
	}

}
