package br.com.lhamalog.core.upload;

/** Failure talking to the Collector (timeout, connection error, or a surprising status). */
public class CollectorForwardException extends RuntimeException {

	private final boolean timeout;

	public CollectorForwardException(String message, boolean timeout, Throwable cause) {
		super(message, cause);
		this.timeout = timeout;
	}

	public boolean timeout() {
		return timeout;
	}
}
