package br.com.lhamalog.core.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenObserveStreamNamesTest {

	@ParameterizedTest
	@CsvSource({
			"llt187-dash-tenant, logs_llt187_dash_tenant_94439d83",
			"testenv-tenant-active, logs_testenv_tenant_active_51c8a9ea",
			"acme, logs_acme",
			"org_a, logs_org_a"
	})
	void resolvesCollectorExamples(String tenantId, String expectedStream) {
		assertEquals(expectedStream, OpenObserveStreamNames.resolve("logs", tenantId));
	}

	@Test
	void usesCustomPrefix() {
		assertEquals("events_acme", OpenObserveStreamNames.resolve("events", "acme"));
	}

	@Test
	void sanitizesInvalidCharactersAndHashes() {
		String stream = OpenObserveStreamNames.resolve("logs", "Tenant.With Spaces!");
		assertTrue(stream.startsWith("logs_tenant_with_spaces__"));
		assertEquals(8, stream.substring(stream.lastIndexOf('_') + 1).length());
		assertEquals(OpenObserveStreamNames.shortHash("Tenant.With Spaces!"),
				stream.substring(stream.lastIndexOf('_') + 1));
	}

	@Test
	void truncatesLongSegmentAndHashes() {
		String tenantId = "a".repeat(45);
		String stream = OpenObserveStreamNames.resolve("logs", tenantId);
		assertEquals("logs_" + "a".repeat(40) + "_" + OpenObserveStreamNames.shortHash(tenantId), stream);
	}

	@Test
	void rejectsBlankPrefix() {
		assertThrows(IllegalArgumentException.class, () -> OpenObserveStreamNames.resolve(" ", "acme"));
	}
}
