package br.com.lhamalog.core.upload;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** DTOs of {@code POST /api/manual/upload} and of the Collector import contract. */
public final class ManualUploadResponses {

	private ManualUploadResponses() {
	}

	public static final String ACCEPTED = "ACCEPTED";
	public static final String DUPLICATE = "DUPLICATE";
	public static final String INVALID_EXTENSION = "INVALID_EXTENSION";
	public static final String TOO_LARGE = "TOO_LARGE";
	public static final String OBVIOUS_BINARY = "OBVIOUS_BINARY";
	public static final String UNKNOWN = "UNKNOWN";
	public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
	public static final String COLLECTOR_RESULT_MISSING = "collector_result_missing";

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Body(String loteId, List<FileResult> files) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileResult(String name, String fileId, String batchId, String status, String reason) {

		public static FileResult accepted(String name, String fileId, String batchId) {
			return new FileResult(name, fileId, batchId, ACCEPTED, null);
		}

		public static FileResult refused(String name, String status, String reason) {
			return new FileResult(name, null, null, status, reason);
		}
	}

	static Body body(String loteId, List<FileResult> files) {
		return new Body(loteId, List.copyOf(files));
	}
}
