package br.com.lhamalog.core.logging;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {
	public static final String KEY = "correlationId";
	public static final String HEADER = "X-Correlation-Id";
	private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

	private CorrelationId() {
	}

	public static boolean isValid(String value) {
		return value != null && VALID.matcher(value).matches();
	}

	public static String newId() {
		return UUID.randomUUID().toString();
	}

	public static String resolve(String inbound) {
		return isValid(inbound) ? inbound : newId();
	}
}
