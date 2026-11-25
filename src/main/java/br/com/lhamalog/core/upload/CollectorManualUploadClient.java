package br.com.lhamalog.core.upload;

import java.util.List;

import org.springframework.http.HttpHeaders;

import br.com.lhamalog.core.upload.ManualUploadResponses.Body;

/** Forwards a manual-upload lote to the Collector. Tests replace the HTTP implementation with a recording fake. */
public interface CollectorManualUploadClient {

	record Request(String loteId, String tenantId, String productId, String correlationId,
			List<OutboundUploadFile> files) {
	}

	record Response(int status, Body body, HttpHeaders headers) {

		public Response(int status, Body body) {
			this(status, body, HttpHeaders.EMPTY);
		}

		public String retryAfter() {
			if (headers == null) {
				return null;
			}
			String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
			return value == null || value.isBlank() ? null : value;
		}
	}

	Response forward(Request request);
}
