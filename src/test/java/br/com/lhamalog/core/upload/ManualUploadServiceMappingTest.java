package br.com.lhamalog.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;

class ManualUploadServiceMappingTest {

	@Test
	void conflictPlusLocalTooLargeBecomes413() {
		ProductUploadAuthorizer authorizer = mock(ProductUploadAuthorizer.class);
		doNothing().when(authorizer).requireUploadable(any(), anyString());
		ManualUploadValidator validator = new ManualUploadValidator(new ManualUploadProperties(List.of("log"),
				"http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(1), DataSize.ofMegabytes(10),
				DataSize.ofMegabytes(100)));
		CollectorManualUploadClient collector = (request) -> new CollectorManualUploadClient.Response(409,
				new Body(request.loteId(),
						List.of(FileResult.refused("ok.log", ManualUploadResponses.DUPLICATE,
								ManualUploadResponses.ALREADY_EXISTS))));
		ManualUploadService service = new ManualUploadService(authorizer, validator, collector);
		ResponseEntity<Body> response = service.upload(new CompanySession("tenant-a", "user", true), "product-b",
				List.of(oversized("huge.log", DataSize.ofMegabytes(11).toBytes()),
						new MockMultipartFile("files", "ok.log", "text/plain", "ok".getBytes())));
		assertEquals(413, response.getStatusCode().value());
		assertEquals(ManualUploadResponses.TOO_LARGE, response.getBody().files().get(0).status());
		assertEquals(ManualUploadResponses.DUPLICATE, response.getBody().files().get(1).status());
	}

	@Test
	void blankCollectorUrlWithTokenReturns503Not500() {
		ProductUploadAuthorizer authorizer = mock(ProductUploadAuthorizer.class);
		doNothing().when(authorizer).requireUploadable(any(), anyString());
		ManualUploadValidator validator = new ManualUploadValidator(new ManualUploadProperties(List.of("log"),
				"", Duration.ofSeconds(5), Duration.ofSeconds(1), DataSize.ofMegabytes(100),
				DataSize.ofMegabytes(100)));
		RestCollectorManualUploadClient collector = new RestCollectorManualUploadClient(RestClient.builder(),
				new ManualUploadProperties(List.of("log"), "   ", Duration.ofSeconds(5), Duration.ofSeconds(1),
						DataSize.ofMegabytes(100), DataSize.ofMegabytes(100)),
				"service-token");
		ManualUploadService service = new ManualUploadService(authorizer, validator, collector);
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.upload(new CompanySession("tenant-a", "user", true), "product-b",
						List.of(new MockMultipartFile("files", "ok.log", "text/plain", "ok".getBytes()))));
		assertEquals(503, exception.getStatusCode().value());
	}

	@Test
	void collectorUrlWithoutSchemeReturns503Not500() {
		ProductUploadAuthorizer authorizer = mock(ProductUploadAuthorizer.class);
		doNothing().when(authorizer).requireUploadable(any(), anyString());
		ManualUploadValidator validator = new ManualUploadValidator(new ManualUploadProperties(List.of("log"),
				"http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(1), DataSize.ofMegabytes(100),
				DataSize.ofMegabytes(100)));
		RestCollectorManualUploadClient collector = new RestCollectorManualUploadClient(RestClient.builder(),
				new ManualUploadProperties(List.of("log"), "lhamalog-collector:8080", Duration.ofSeconds(5),
						Duration.ofSeconds(1), DataSize.ofMegabytes(100), DataSize.ofMegabytes(100)),
				"service-token");
		ManualUploadService service = new ManualUploadService(authorizer, validator, collector);
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.upload(new CompanySession("tenant-a", "user", true), "product-b",
						List.of(new MockMultipartFile("files", "ok.log", "text/plain", "ok".getBytes()))));
		assertEquals(503, exception.getStatusCode().value());
	}

	private static MockMultipartFile oversized(String name, long size) {
		return new MockMultipartFile("files", name, "text/plain", new byte[0]) {
			@Override
			public long getSize() {
				return size;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};
	}
}
