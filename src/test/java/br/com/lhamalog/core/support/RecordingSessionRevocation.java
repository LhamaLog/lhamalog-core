package br.com.lhamalog.core.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import br.com.lhamalog.core.identity.application.port.out.SessionRevocation;

/** Fake of the revocation port: records the calls and can be told to fail. */
public class RecordingSessionRevocation implements SessionRevocation {

	private final List<String> revokedUsers = new CopyOnWriteArrayList<>();

	private volatile boolean failing;

	@Override
	public void revokeAllActiveSessions(String clerkUserId) {
		revokedUsers.add(clerkUserId);
		if (failing) {
			throw new IllegalStateException("Simulated Clerk outage");
		}
	}

	public List<String> revokedUsers() {
		return List.copyOf(revokedUsers);
	}

	public void failNextCalls() {
		this.failing = true;
	}

	public void reset() {
		revokedUsers.clear();
		failing = false;
	}

}
