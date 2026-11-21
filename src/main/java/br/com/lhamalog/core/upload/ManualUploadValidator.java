package br.com.lhamalog.core.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;

/** Extension and size checks applied in the Core before the Collector is called. */
@Component
public class ManualUploadValidator {

	private static final Logger log = LoggerFactory.getLogger(ManualUploadValidator.class);

	private final Set<String> allowedExtensions;

	private final long maxFileBytes;

	private final long maxRequestBytes;

	private final String maxRequestSizeLabel;

	public ManualUploadValidator(ManualUploadProperties properties) {
		this.allowedExtensions = properties.extensionSet();
		this.maxFileBytes = properties.maxFileBytes();
		this.maxRequestBytes = properties.maxRequestBytes();
		this.maxRequestSizeLabel = properties.maxRequestSizeLabel();
	}

	public record CheckedFile(MultipartFile file, FileResult refusal) {

		boolean accepted() {
			return refusal == null;
		}
	}

	public record Outcome(List<CheckedFile> files) {

		public List<MultipartFile> accepted() {
			return files.stream().filter(CheckedFile::accepted).map(CheckedFile::file).toList();
		}

		public List<FileResult> refused() {
			return files.stream().filter((item) -> !item.accepted()).map(CheckedFile::refusal).toList();
		}

		/**
		 * Merges Collector results into the original request order (local refusals stay in place).
		 * Association is by position and, when the Collector item has a {@code name}, by original
		 * filename. A count mismatch is logged. An accepted part with no matching Collector item stays
		 * in {@code files[]} as {@code UNKNOWN}/{@code collector_result_missing}, in request order.
		 */
		public List<FileResult> mergeCollector(List<FileResult> collectorFiles) {
			List<FileResult> incoming = collectorFiles == null ? List.of() : collectorFiles;
			int acceptedCount = 0;
			for (CheckedFile item : files) {
				if (item.accepted()) {
					acceptedCount++;
				}
			}
			if (incoming.size() != acceptedCount) {
				log.warn(
						"event=manual_upload.collector_result_count_mismatch | Collector file count differs from accepted parts | accepted={} returned={}",
						acceptedCount, incoming.size());
			}
			boolean[] used = new boolean[incoming.size()];
			List<FileResult> merged = new ArrayList<>(files.size());
			int acceptedIndex = 0;
			for (CheckedFile item : files) {
				if (!item.accepted()) {
					merged.add(item.refusal());
					continue;
				}
				String name = originalName(item.file());
				FileResult match = takeCollectorResult(incoming, used, acceptedIndex, name);
				acceptedIndex++;
				if (match != null) {
					merged.add(match);
				}
				else {
					log.warn(
							"event=manual_upload.collector_result_missing | Accepted part has no matching Collector item | index={} filename={}",
							acceptedIndex - 1, name);
					merged.add(FileResult.refused(name, ManualUploadResponses.UNKNOWN,
							ManualUploadResponses.COLLECTOR_RESULT_MISSING));
				}
			}
			return List.copyOf(merged);
		}
	}

	public Outcome validate(List<MultipartFile> files) {
		if (files == null || files.isEmpty() || files.stream().allMatch(ManualUploadValidator::isBlank)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie pelo menos um arquivo.");
		}
		long total = 0;
		for (MultipartFile file : files) {
			if (!isBlank(file)) {
				total = Math.addExact(total, Math.max(0L, file.getSize()));
			}
		}
		if (total > maxRequestBytes) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
					"A requisição excede o limite de " + maxRequestSizeLabel + ".");
		}
		List<CheckedFile> checked = new ArrayList<>();
		for (MultipartFile file : files) {
			String name = originalName(file);
			if (isBlank(file)) {
				checked.add(new CheckedFile(file, FileResult.refused(name, ManualUploadResponses.INVALID_EXTENSION, "empty_file")));
				continue;
			}
			if (file.getSize() > maxFileBytes) {
				checked.add(new CheckedFile(file, FileResult.refused(name, ManualUploadResponses.TOO_LARGE, "max_file_size")));
				continue;
			}
			if (!allowedExtensions.contains(extensionOf(name))) {
				checked.add(new CheckedFile(file, FileResult.refused(name, ManualUploadResponses.INVALID_EXTENSION, "extension_not_allowed")));
				continue;
			}
			checked.add(new CheckedFile(file, null));
		}
		return new Outcome(List.copyOf(checked));
	}

	public HttpStatus allRefusedStatus(List<FileResult> refused) {
		boolean anyTooLarge = refused.stream().anyMatch((item) -> ManualUploadResponses.TOO_LARGE.equals(item.status()));
		return anyTooLarge ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
	}

	static FileResult takeCollectorResult(List<FileResult> incoming, boolean[] used, int acceptedIndex,
			String originalName) {
		if (acceptedIndex < incoming.size() && !used[acceptedIndex]
				&& nameCompatible(incoming.get(acceptedIndex).name(), originalName)) {
			used[acceptedIndex] = true;
			return incoming.get(acceptedIndex);
		}
		if (originalName != null && !originalName.isBlank()) {
			for (int index = 0; index < incoming.size(); index++) {
				if (!used[index] && namesEqual(incoming.get(index).name(), originalName)) {
					used[index] = true;
					return incoming.get(index);
				}
			}
		}
		return null;
	}

	static boolean nameCompatible(String collectorName, String originalName) {
		return collectorName == null || collectorName.isBlank() || namesEqual(collectorName, originalName);
	}

	static boolean namesEqual(String collectorName, String originalName) {
		return basename(collectorName).equals(basename(originalName));
	}

	static String basename(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		return slash >= 0 ? name.substring(slash + 1) : name;
	}

	static String originalName(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "file";
		}
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		return slash >= 0 ? name.substring(slash + 1) : name;
	}

	static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean isBlank(MultipartFile file) {
		return file == null || file.isEmpty() || file.getSize() <= 0;
	}
}
