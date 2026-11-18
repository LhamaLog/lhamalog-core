package br.com.lhamalog.core.identity.domain;

import java.util.Objects;

/** Outcome of the access check made after the token is authenticated. */
public sealed interface AccessDecision {

	record Granted(RequestContext context) implements AccessDecision {

		public Granted {
			Objects.requireNonNull(context, "context");
		}

	}

	/** The reason is only logged, never returned to the client. */
	record Denied(Reason reason) implements AccessDecision {

		public Denied {
			Objects.requireNonNull(reason, "reason");
		}

	}

	enum Reason {

		NO_ACTIVE_ORGANIZATION,

		UNKNOWN_ROLE,

		TENANT_NOT_FOUND,

		TENANT_INACTIVE

	}

}
