package br.com.lhamalog.core.shared;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	private final UploadRequestSizeLabel uploadRequestSizeLabel;

	@Autowired
	public ApiExceptionHandler(ObjectProvider<UploadRequestSizeLabel> uploadRequestSizeLabel) {
		this.uploadRequestSizeLabel = uploadRequestSizeLabel.getIfAvailable();
	}

	public ApiExceptionHandler(UploadRequestSizeLabel uploadRequestSizeLabel) {
		this.uploadRequestSizeLabel = uploadRequestSizeLabel;
	}

	private String maxRequestLabel() {
		return uploadRequestSizeLabel == null ? "100 MB" : uploadRequestSizeLabel.maxRequestSizeLabel();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ApiError(String message, Map<String, String> fieldErrors, String code) {
		public ApiError(String message, Map<String, String> fieldErrors) {
			this(message, fieldErrors, null);
		}
	}

	@ExceptionHandler(FieldValidationException.class)
	ResponseEntity<ApiError> fields(FieldValidationException exception) {
		return ResponseEntity.badRequest().body(new ApiError(exception.getMessage(), exception.fieldErrors()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
		if (isLogoUpload(request)) {
			return error(HttpStatus.PAYLOAD_TOO_LARGE, "O logotipo excede 2 MB.");
		}
		return error(HttpStatus.PAYLOAD_TOO_LARGE, containerUploadMessage(exception, maxRequestLabel()));
	}

	static boolean isLogoUpload(HttpServletRequest request) {
		if (request == null) {
			return false;
		}
		String uri = request.getRequestURI();
		return uri != null && uri.contains("/api/company/logo");
	}

	/**
	 * Classifies a container-level 413. Tomcat 11.0.24 reports a part-count failure as
	 * {@code FileCountLimitExceededException: attachment} (no word "part") and a part-header failure as
	 * {@code Header section has more than 512 bytes (maybe it is not properly terminated)}. Spring wraps
	 * both in {@link MaxUploadSizeExceededException}.
	 */
	static String containerUploadMessage(Throwable exception, String maxRequestLabel) {
		String combined = joinMessages(exception).toLowerCase(Locale.ROOT);
		if (hasCauseNamed(exception, "FileCountLimitExceededException") || looksLikePartCount(combined)) {
			return "A requisição excede o número máximo de partes permitidas.";
		}
		if (combined.contains("header section has more than") || looksLikePartHeader(combined)) {
			return "Um cabeçalho de parte da requisição excede o limite permitido.";
		}
		return "A requisição excede o limite de " + maxRequestLabel + ".";
	}

	private static boolean looksLikePartCount(String combined) {
		return combined.contains("part-count") || combined.contains("too many parts")
				|| combined.contains("number of parts")
				|| (combined.contains("max") && combined.contains("part") && combined.contains("count"));
	}

	private static boolean looksLikePartHeader(String combined) {
		return combined.contains("part-header") || combined.contains("part header")
				|| combined.contains("header size") || combined.contains("max-part-header");
	}

	static boolean hasCauseNamed(Throwable exception, String simpleName) {
		Throwable current = exception;
		int depth = 0;
		while (current != null && depth < 8) {
			if (simpleName.equals(current.getClass().getSimpleName())) {
				return true;
			}
			current = current.getCause();
			depth++;
		}
		return false;
	}

	private static String joinMessages(Throwable exception) {
		StringBuilder text = new StringBuilder();
		Throwable current = exception;
		int depth = 0;
		while (current != null && depth < 8) {
			text.append(' ').append(current.getClass().getName());
			if (current.getMessage() != null) {
				text.append(' ').append(current.getMessage());
			}
			current = current.getCause();
			depth++;
		}
		return text.toString();
	}

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

	@ExceptionHandler(ApiStatusException.class)
	ResponseEntity<ApiError> coded(ApiStatusException exception) {
		return ResponseEntity.status(exception.getStatusCode())
				.body(new ApiError(exception.getReason(), Map.of(), exception.code()));
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
