package br.com.lhamalog.core.finderalert;

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

import br.com.lhamalog.core.occurrence.application.InternalApiException;

/**
 * Errors of the Finder alert route as {@code {"codigo": "..."}}. The body, the token and any log excerpt stay out of
 * the log. This route logs only {@code occurrence.alert.received}, {@code occurrence.alert.accepted},
 * {@code occurrence.alert.duplicate} and {@code occurrence.alert.rejected}.
 */
@RestControllerAdvice(assignableTypes = FinderAlertController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinderAlertErrors {

	private static final Logger log = LoggerFactory.getLogger(FinderAlertErrors.class);

	@ExceptionHandler(InternalApiException.class)
	ResponseEntity<FinderAlertError> refused(InternalApiException refused) {
		rejected(refused.code());
		return ResponseEntity.status(refused.status()).body(new FinderAlertError(refused.code()));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class })
	ResponseEntity<FinderAlertError> unreadable(Exception unreadable) {
		log.info("event=occurrence.alert.received | Finder alert received");
		rejected(InternalApiException.INVALID_REQUEST);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new FinderAlertError(InternalApiException.INVALID_REQUEST));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<FinderAlertError> unexpected(Exception failure) {
		log.error("event=occurrence.internal.failed | Unexpected failure in the Finder alert route | error={}",
				failure.getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new FinderAlertError(InternalApiException.INTERNAL_ERROR));
	}

	private static void rejected(String code) {
		log.warn("event=occurrence.alert.rejected | Finder alert refused | codigo={}", code);
	}

	/** {@code Erro} of this route. */
	public record FinderAlertError(String codigo) {
	}

}
