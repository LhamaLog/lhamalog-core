package br.com.lhamalog.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.shared.ApiExceptionHandler.ApiError;

/**
 * Container-level 413 mapping. MockMvc does not enforce {@code spring.servlet.multipart.*} or Tomcat
 * {@code max-part-count} / {@code max-part-header-size}, so these paths are unit-tested on the handler (I2, I6).
 * The wrapped texts and cause types are those of Tomcat 11.0.24 (N2).
 */
class ApiExceptionHandlerUploadTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler(new ManualUploadProperties(List.of("log"),
			"http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(120), DataSize.ofMegabytes(100),
			DataSize.ofMegabytes(100)));

	@Test
	void sizeExceededUsesTheConfiguredLimitLabel() {
		MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(DataSize.ofMegabytes(100).toBytes());
		ResponseEntity<ApiError> response = handler.uploadTooLarge(exception, request("/api/manual/upload"));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertEquals("A requisição excede o limite de 100 MB.", response.getBody().message());
		assertTrue(response.getBody().fieldErrors().isEmpty());
	}

	@Test
	void partCountAndPartHeaderAreNotDescribedAsASizeLimit() {
		FileCountLimitExceededException count = new FileCountLimitExceededException("attachment", 50);
		InvalidParameterException countWrapper = new InvalidParameterException(count, 413);
		ResponseEntity<ApiError> parts = handler.uploadTooLarge(new MaxUploadSizeExceededException(-1, countWrapper),
				request("/api/manual/upload"));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, parts.getStatusCode());
		assertEquals("A requisição excede o número máximo de partes permitidas.", parts.getBody().message());

		SizeLimitExceededException header = new SizeLimitExceededException(
				"Header section has more than 512 bytes (maybe it is not properly terminated)", 600, 512);
		InvalidParameterException headerWrapper = new InvalidParameterException(header, 413);
		ResponseEntity<ApiError> headerResponse = handler.uploadTooLarge(
				new MaxUploadSizeExceededException(-1, headerWrapper), request("/api/manual/upload"));
		assertEquals("Um cabeçalho de parte da requisição excede o limite permitido.",
				headerResponse.getBody().message());
	}

	@Test
	void logoPathSpeaksOfTwoMegabytes() {
		ResponseEntity<ApiError> response = handler.uploadTooLarge(
				new MaxUploadSizeExceededException(DataSize.ofMegabytes(100).toBytes()),
				request("/api/company/logo"));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertEquals("O logotipo excede 2 MB.", response.getBody().message());
	}

	private static MockHttpServletRequest request(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		request.setRequestURI(uri);
		return request;
	}
}
