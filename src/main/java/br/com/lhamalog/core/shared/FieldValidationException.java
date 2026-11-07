package br.com.lhamalog.core.shared;

import java.util.Map;

public class FieldValidationException extends RuntimeException {
	private final Map<String, String> fieldErrors;

	public FieldValidationException(Map<String, String> fieldErrors) {
		super("Confira os campos informados.");
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> fieldErrors() {
		return fieldErrors;
	}
}
