package br.com.lhamalog.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ManualUploadStartupChecksTest {

	@Test
	void warnsWhenTheTokenIsEmptyOutsideLocal() {
		List<String> warnings = ManualUploadStartupChecks.warnings(false, "  ", "http://collector:8080", 8080);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("collector_token_missing"));
	}

	@Test
	void doesNotWarnAboutAMissingTokenOnTheLocalProfile() {
		List<String> warnings = ManualUploadStartupChecks.warnings(true, "", "http://collector:8080", 8080);
		assertTrue(warnings.isEmpty());
	}

	@Test
	void warnsWhenTheCollectorUrlPointsAtTheCorePort() {
		List<String> warnings = ManualUploadStartupChecks.warnings(true, "token", "http://localhost:8080", 8080);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("collector_url_self"));
		assertTrue(ManualUploadStartupChecks.looksLikeCorePort("http://127.0.0.1:8081", 8081));
		assertFalse(ManualUploadStartupChecks.looksLikeCorePort("http://lhamalog-collector:8080", 8080));
	}

	@Test
	void warnsWhenTheCollectorUrlIsBlank() {
		List<String> warnings = ManualUploadStartupChecks.warnings(true, "token", "  ", 8080);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("collector_url_missing"));
		List<String> unset = ManualUploadStartupChecks.warnings(false, "token", "", 8080);
		assertEquals(1, unset.size());
		assertTrue(unset.get(0).contains("collector_url_missing"));
		assertFalse(ManualUploadStartupChecks.looksLikeCorePort("", 8080));
	}

	@Test
	void warnsWhenTheCollectorUrlHasNoHttpScheme() {
		List<String> warnings = ManualUploadStartupChecks.warnings(true, "token", "lhamalog-collector:8080", 8080);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("collector_url_invalid"));
		assertFalse(ManualUploadProperties.isUsableCollectorUrl("lhamalog-collector:8080"));
		assertFalse(ManualUploadProperties.isUsableCollectorUrl(""));
		assertTrue(ManualUploadProperties.isUsableCollectorUrl("http://lhamalog-collector:8080"));
		assertTrue(ManualUploadProperties.isUsableCollectorUrl("https://collector.example:8443"));
	}
}
