package br.com.lhamalog.core.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.logging.CorrelationId;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;

/** Authorizes the product, validates files, waits for the Collector and maps the decision to the public API. */
@Service
public class ManualUploadService {

	private static final Logger log = LoggerFactory.getLogger(ManualUploadService.class);

	private final ProductUploadAuthorizer authorizer;

	private final ManualUploadValidator validator;

	private final CollectorManualUploadClient collector;

	public ManualUploadService(ProductUploadAuthorizer authorizer, ManualUploadValidator validator,
			CollectorManualUploadClient collector) {
		this.authorizer = authorizer;
		this.validator = validator;
		this.collector = collector;
	}

	public ResponseEntity<Body> upload(CompanySession session, String productId, List<MultipartFile> files) {
		String normalizedProductId = normalizeProductId(productId);
		authorizer.requireUploadable(session, normalizedProductId);
		ManualUploadValidator.Outcome outcome = validator.validate(files);
		if (outcome.accepted().isEmpty()) {
			HttpStatus status = validator.allRefusedStatus(outcome.refused());
			log.info(
					"event=manual_upload.rejected | Manual upload refused before the Collector | tenantId={} productId={} status={} files={}",
					session.tenantId(), normalizedProductId, status.value(), outcome.refused().size());
			return ResponseEntity.status(status).body(ManualUploadResponses.body(null, outcome.refused()));
		}
		String loteId = UUID.randomUUID().toString();
		String correlationId = correlationId();
		CollectorManualUploadClient.Response collectorResponse;
		try {
			collectorResponse = collector.forward(new CollectorManualUploadClient.Request(loteId, session.tenantId(),
					normalizedProductId, correlationId, toOutbound(outcome.accepted())));
		}
		catch (CollectorForwardException exception) {
			HttpStatus status = exception.timeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
			log.warn(
					"event=manual_upload.collector_unavailable | Collector did not accept the manual upload | tenantId={} productId={} loteId={} status={} timeout={} cause={}",
					session.tenantId(), normalizedProductId, loteId, status.value(), exception.timeout(),
					causeName(exception));
			throw new ResponseStatusException(status, exception.timeout()
					? "O Collector não respondeu a tempo. O resultado do envio é desconhecido."
					: "O Collector está indisponível.", exception);
		}
		return mapCollector(loteId, session.tenantId(), normalizedProductId, outcome, collectorResponse);
	}

	private ResponseEntity<Body> mapCollector(String loteId, String tenantId, String productId,
			ManualUploadValidator.Outcome outcome, CollectorManualUploadClient.Response collectorResponse) {
		int status = collectorResponse.status();
		List<FileResult> collectorFiles = collectorResponse.body() == null ? List.of()
				: collectorResponse.body().files();
		List<FileResult> files = outcome.mergeCollector(collectorFiles);
		String responseLoteId = collectorResponse.body() != null && collectorResponse.body().loteId() != null
				? collectorResponse.body().loteId()
				: loteId;
		if (status == HttpStatus.UNAUTHORIZED.value()) {
			log.warn(
					"event=manual_upload.collector_unauthorized | Collector rejected the service token | tenantId={} productId={} loteId={} status={}",
					tenantId, productId, responseLoteId, status);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O Collector está indisponível.");
		}
		if (!outcome.refused().isEmpty() && status == HttpStatus.CONFLICT.value()) {
			// Q8: 409 only when every file is a duplicate. Local refusals change that;
			// TOO_LARGE keeps 413 via allRefusedStatus (S4).
			status = validator.allRefusedStatus(outcome.refused()).value();
		}
		if (status == HttpStatus.ACCEPTED.value() || status == HttpStatus.CONFLICT.value()
				|| status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.PAYLOAD_TOO_LARGE.value()
				|| status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value()
				|| status == HttpStatus.GATEWAY_TIMEOUT.value()) {
			log.info(
					"event=manual_upload.completed | Manual upload finished | tenantId={} productId={} loteId={} status={} files={}",
					tenantId, productId, responseLoteId, status, files.size());
			ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
			String retryAfter = collectorResponse.retryAfter();
			if (retryAfter != null) {
				builder.header(HttpHeaders.RETRY_AFTER, retryAfter);
			}
			return builder.body(ManualUploadResponses.body(responseLoteId, files));
		}
		log.warn(
				"event=manual_upload.collector_unavailable | Collector returned an unexpected status | tenantId={} productId={} loteId={} status={}",
				tenantId, productId, responseLoteId, status);
		throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O Collector está indisponível.");
	}

	static String normalizeProductId(String productId) {
		if (productId == null || productId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o productId do card.");
		}
		String value = productId.strip();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '\r' || character == '\n' || character == '\u0000') {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o productId do card.");
			}
		}
		return value;
	}

	static String causeName(Throwable exception) {
		Throwable cause = exception == null ? null : exception.getCause();
		if (cause == null) {
			return "none";
		}
		return cause.getClass().getSimpleName();
	}

	private static List<OutboundUploadFile> toOutbound(List<MultipartFile> files) {
		List<OutboundUploadFile> outbound = new ArrayList<>(files.size());
		for (MultipartFile file : files) {
			MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
			if (file.getContentType() != null && !file.getContentType().isBlank()) {
				try {
					contentType = MediaType.parseMediaType(file.getContentType());
				}
				catch (RuntimeException ignored) {
					contentType = MediaType.APPLICATION_OCTET_STREAM;
				}
			}
			Resource resource = file.getResource();
			outbound.add(new OutboundUploadFile(ManualUploadValidator.originalName(file), contentType, resource,
					file.getSize()));
		}
		return List.copyOf(outbound);
	}

	private static String correlationId() {
		String current = MDC.get(CorrelationId.KEY);
		return CorrelationId.isValid(current) ? current : CorrelationId.newId();
	}
}
