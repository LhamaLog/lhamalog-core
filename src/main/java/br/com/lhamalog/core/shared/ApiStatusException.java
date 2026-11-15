package br.com.lhamalog.core.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiStatusException extends ResponseStatusException {
	private final String code;

	public ApiStatusException(HttpStatus status, String message, String code) {
		super(status, message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
