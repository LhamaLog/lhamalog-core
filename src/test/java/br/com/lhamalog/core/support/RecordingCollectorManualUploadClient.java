package br.com.lhamalog.core.support;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import br.com.lhamalog.core.upload.CollectorForwardException;
import br.com.lhamalog.core.upload.CollectorManualUploadClient;
import br.com.lhamalog.core.upload.ManualUploadResponses;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;
import br.com.lhamalog.core.upload.OutboundUploadFile;

/** In-process Collector fake used by HTTP tests so the shared Spring context never talks to a real Collector. */
public class RecordingCollectorManualUploadClient implements CollectorManualUploadClient {

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private volatile Function<Request, Response> handler = this::autoAccept;

	private volatile CollectorForwardException failure;

	@Override
	public Response forward(Request request) {
		requests.add(request);
		CollectorForwardException pending = this.failure;
		if (pending != null) {
			this.failure = null;
			throw pending;
		}
		return handler.apply(request);
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	public void respond(Function<Request, Response> next) {
		this.handler = next;
	}

	public void respond(int status, Body body) {
		this.handler = (request) -> new Response(status, body);
	}

	public void fail(CollectorForwardException exception) {
		this.failure = exception;
	}

	public void reset() {
		requests.clear();
		failure = null;
		handler = this::autoAccept;
	}

	private Response autoAccept(Request request) {
		List<FileResult> files = request.files()
			.stream()
			.map(RecordingCollectorManualUploadClient::accepted)
			.toList();
		return new Response(202, new Body(request.loteId(), files));
	}

	private static FileResult accepted(OutboundUploadFile file) {
		return FileResult.accepted(file.filename(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
	}
}
