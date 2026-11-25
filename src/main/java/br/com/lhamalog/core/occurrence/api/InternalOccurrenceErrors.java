package br.com.lhamalog.core.occurrence.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.lhamalog.core.occurrence.api.InternalOccurrenceResponses.Erro;
import br.com.lhamalog.core.occurrence.application.InternalApiException;

/**
 * Errors of the internal routes in the contract format {@code {"codigo": "..."}} (research R5). Only for
 * {@link InternalOccurrenceController}; the general {@code ApiExceptionHandler} keeps serving the user routes.
 */
@RestControllerAdvice(assignableTypes = InternalOccurrenceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalOccurrenceErrors {

	private static final Logger log = LoggerFactory.getLogger(InternalOccurrenceErrors.class);

	@ExceptionHandler(InternalApiException.class)
	ResponseEntity<Erro> refused(InternalApiException refused) {
		return ResponseEntity.status(refused.status()).body(new Erro(refused.code()));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class })
	ResponseEntity<Erro> unreadable(Exception unreadable) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Erro(InternalApiException.INVALID_REQUEST));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Erro> unexpected(Exception failure) {
		// No request content in the log: only the failure type.
		log.error("event=occurrence.internal.failed | Unexpected failure in an internal occurrence route | error={}",
				failure.getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Erro(InternalApiException.INTERNAL_ERROR));
	}

}
