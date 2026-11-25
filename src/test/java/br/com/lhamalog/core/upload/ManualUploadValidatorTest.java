package br.com.lhamalog.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.config.ManualUploadProperties;
import br.com.lhamalog.core.upload.ManualUploadResponses.FileResult;

class ManualUploadValidatorTest {

	private final ManualUploadValidator validator = new ManualUploadValidator(new ManualUploadProperties(
			List.of("txt", ".JSON", "csv", "log"), "http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(1),
			DataSize.ofMegabytes(100), DataSize.ofMegabytes(100)));

	@Test
	void acceptsConfiguredExtensionsRegardlessOfCase() {
		ManualUploadValidator.Outcome outcome = validator
			.validate(List.of(file("APP.TXT", 12), file("data.JSON", 20)));
		assertEquals(2, outcome.accepted().size());
		assertTrue(outcome.refused().isEmpty());
	}

	@Test
	void refusesUnknownExtensionAndEmptyFile() {
		ManualUploadValidator.Outcome outcome = validator
			.validate(List.of(file("notes.exe", 10), file("empty.log", 0)));
		assertTrue(outcome.accepted().isEmpty());
		assertEquals(ManualUploadResponses.INVALID_EXTENSION, outcome.refused().get(0).status());
		assertEquals(ManualUploadResponses.INVALID_EXTENSION, outcome.refused().get(1).status());
		assertEquals(HttpStatus.BAD_REQUEST, validator.allRefusedStatus(outcome.refused()));
	}

	@Test
	void refusesFileOverTheLimit() {
		ManualUploadValidator tightFileLimit = new ManualUploadValidator(new ManualUploadProperties(
				List.of("log"), "http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(1),
				DataSize.ofMegabytes(10), DataSize.ofMegabytes(100)));
		ManualUploadValidator.Outcome outcome = tightFileLimit.validate(List.of(file("huge.log", DataSize.ofMegabytes(11).toBytes())));
		assertEquals(ManualUploadResponses.TOO_LARGE, outcome.refused().get(0).status());
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, tightFileLimit.allRefusedStatus(outcome.refused()));
	}

	@Test
	void rejectsSingleFileWhenItExceedsBothLimits() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> validator.validate(List.of(file("huge.log", DataSize.ofMegabytes(100).toBytes() + 1))));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
	}

	@Test
	void rejectsRequestWhenTheSumExceedsTheLimit() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> validator.validate(List.of(file("a.log", DataSize.ofMegabytes(60).toBytes()),
						file("b.log", DataSize.ofMegabytes(60).toBytes()))));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
	}

	@Test
	void rejectsMissingFiles() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> validator.validate(List.of()));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void mergeKeepsRequestOrderAndMatchesByNameWhenCollectorOrderDiffers() {
		ManualUploadValidator.Outcome outcome = validator.validate(
				List.of(file("a.log", 4), file("skip.exe", 4), file("c.log", 4)));
		List<FileResult> merged = outcome.mergeCollector(List.of(
				FileResult.accepted("c.log", "fc", "bc"),
				FileResult.accepted("a.log", "fa", "ba")));
		assertEquals(List.of("a.log", "skip.exe", "c.log"), merged.stream().map(FileResult::name).toList());
		assertEquals("fa", merged.get(0).fileId());
		assertEquals(ManualUploadResponses.INVALID_EXTENSION, merged.get(1).status());
		assertEquals("fc", merged.get(2).fileId());
	}

	@Test
	void mergeLogsWhenCollectorCountDivergesAndDoesNotDropOrAppendSilently() {
		Logger logger = (Logger) LoggerFactory.getLogger(ManualUploadValidator.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			ManualUploadValidator.Outcome outcome = validator.validate(
					List.of(file("keep.log", 4), file("skip.exe", 4), file("missing.log", 4)));
			List<FileResult> shortMerge = outcome.mergeCollector(List.of(
					FileResult.accepted("keep.log", "fk", "bk")));
			assertEquals(List.of("keep.log", "skip.exe", "missing.log"),
					shortMerge.stream().map(FileResult::name).toList());
			assertEquals("fk", shortMerge.get(0).fileId());
			assertEquals(ManualUploadResponses.INVALID_EXTENSION, shortMerge.get(1).status());
			assertEquals(ManualUploadResponses.UNKNOWN, shortMerge.get(2).status());
			assertEquals(ManualUploadResponses.COLLECTOR_RESULT_MISSING, shortMerge.get(2).reason());
			assertTrue(appender.list.stream()
				.anyMatch((event) -> event.getFormattedMessage().contains("collector_result_count_mismatch")));
			assertTrue(appender.list.stream()
				.anyMatch((event) -> event.getFormattedMessage().contains("collector_result_missing")
						&& event.getFormattedMessage().contains("missing.log")));
			appender.list.clear();
			List<FileResult> longMerge = outcome.mergeCollector(List.of(
					FileResult.accepted("keep.log", "fk", "bk"),
					FileResult.accepted("missing.log", "fm", "bm"),
					FileResult.accepted("extra.log", "fx", "bx")));
			assertEquals(List.of("keep.log", "skip.exe", "missing.log"),
					longMerge.stream().map(FileResult::name).toList());
			assertTrue(longMerge.stream().noneMatch((item) -> "extra.log".equals(item.name())));
			assertTrue(appender.list.stream()
				.anyMatch((event) -> event.getFormattedMessage().contains("collector_result_count_mismatch")));
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void stripsDirectoryFromTheOriginalName() {
		assertEquals("app.log", ManualUploadValidator.originalName(file("C:\\\\temp\\\\app.log", 4)));
		assertEquals("app.log", ManualUploadValidator.originalName(file("/var/tmp/app.log", 4)));
	}

	private static MultipartFile file(String originalFilename, long size) {
		return new SizedMultipartFile(originalFilename, size);
	}

	private static final class SizedMultipartFile implements MultipartFile {

		private final String originalFilename;

		private final long size;

		private SizedMultipartFile(String originalFilename, long size) {
			this.originalFilename = originalFilename;
			this.size = size;
		}

		@Override
		public String getName() {
			return "files";
		}

		@Override
		public String getOriginalFilename() {
			return originalFilename;
		}

		@Override
		public String getContentType() {
			return "text/plain";
		}

		@Override
		public boolean isEmpty() {
			return size <= 0;
		}

		@Override
		public long getSize() {
			return size;
		}

		@Override
		public byte[] getBytes() {
			return new byte[0];
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(new byte[0]);
		}

		@Override
		public void transferTo(File dest) throws IOException, IllegalStateException {
			throw new UnsupportedOperationException();
		}
	}
}
