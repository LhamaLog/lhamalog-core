package br.com.lhamalog.core.identity.application.port.out;

/** Output port that ends every active session of a user at the identity provider (ADR_032). */
public interface SessionRevocation {

	/**
	 * Revokes all active sessions of the user. Implementations may throw; callers must not let a failure change the
	 * response sent to the client.
	 */
	void revokeAllActiveSessions(String clerkUserId);

}
