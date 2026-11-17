package br.com.lhamalog.core.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every business 403 must carry a code (ApiStatusException): the Web signs the user out on a 403 without code, which is
 * reserved for the security chain (spec 008, FR-010).
 */
class BusinessForbiddenHasCodeTests {

	private static final Pattern FORBIDDEN_WITHOUT_CODE = Pattern
		.compile("new\\s+ResponseStatusException\\s*\\(\\s*HttpStatus\\.FORBIDDEN");

	@Test
	void noForbiddenResponseStatusExceptionInMainCode() throws IOException {
		Path sources = Path.of("src", "main", "java");
		List<String> offenders;
		try (Stream<Path> files = Files.walk(sources)) {
			offenders = files.filter((file) -> file.toString().endsWith(".java"))
				.filter(BusinessForbiddenHasCodeTests::throwsForbiddenWithoutCode)
				.map((file) -> sources.relativize(file).toString())
				.toList();
		}
		assertThat(offenders).as("use ApiStatusException with a code for business 403").isEmpty();
	}

	private static boolean throwsForbiddenWithoutCode(Path file) {
		try {
			return FORBIDDEN_WITHOUT_CODE.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
