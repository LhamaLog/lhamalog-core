package br.com.lhamalog.core.access.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validates the Clerk user id taken from the path, with the same rule and message as the LLT-168 member routes. */
public final class UserIds {

	public static final int MAX_LENGTH = 200;

	public static final String USER_REQUIRED = "Informe o usuário.";

	private UserIds() {
	}

	/** @throws ResponseStatusException 400 "Informe o usuário." when blank or longer than 200 characters */
	public static String require(String userId) {
		if (userId == null || userId.isBlank() || userId.length() > MAX_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, USER_REQUIRED);
		}
		return userId;
	}

}
