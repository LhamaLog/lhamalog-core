package br.com.lhamalog.core.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
	public record ApiError(String message, Map<String, String> fieldErrors) {}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
		Map<String, String> fields = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fields.putIfAbsent(error.getField(), "Size".equals(error.getCode())
						? "O texto excede o limite permitido." : "Confira o preenchimento deste campo."));
		return ResponseEntity.badRequest().body(new ApiError("Confira os campos informados.", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> invalidBody() {
		return error(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos.");
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiError> status(ResponseStatusException exception) {
		return ResponseEntity.status(exception.getStatusCode())
				.body(new ApiError(exception.getReason(), Map.of()));
	}

	@ExceptionHandler({OptimisticLockingFailureException.class, DuplicateKeyException.class})
	ResponseEntity<ApiError> conflict() {
		return error(HttpStatus.CONFLICT, "Os dados foram alterados. Recarregue antes de salvar novamente.");
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> unavailable() {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível acessar os dados. Tente novamente.");
	}

	private ResponseEntity<ApiError> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiError(message, Map.of()));
	}
}
