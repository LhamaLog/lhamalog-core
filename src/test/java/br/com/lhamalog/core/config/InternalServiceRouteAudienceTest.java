package br.com.lhamalog.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

/** Spec 016: only the Finder alert post selects the core audience inside the single chain. */
class InternalServiceRouteAudienceTest {

	@Test
	void onlyTheFinderAlertPostSelectsTheCoreAudience() {
		assertThat(InternalServiceSecurityConfiguration.finderAlert(request("POST", "/internal/v1/alertas/finder")))
			.isTrue();
		assertThat(InternalServiceSecurityConfiguration.finderAlert(request("POST", "/internal/v1/ocorrencias/consultas")))
			.isFalse();
		assertThat(InternalServiceSecurityConfiguration.finderAlert(request("POST", "/internal/v1/resumos-analise")))
			.isFalse();
		assertThat(InternalServiceSecurityConfiguration.finderAlert(request("GET", "/internal/v1/alertas/finder")))
			.isFalse();
		assertThat(InternalServiceSecurityConfiguration.finderAlert(request("POST", "/internal/v1/alertas/finder/extra")))
			.isFalse();
	}

	private static HttpServletRequest request(String method, String uri) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn(method);
		when(request.getRequestURI()).thenReturn(uri);
		when(request.getContextPath()).thenReturn("");
		return request;
	}

}
