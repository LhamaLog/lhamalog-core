package br.com.lhamalog.core.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestJwks;

/** Security of the Empresa endpoints: no anonymous, invalid or Basic access, CORS only for the front origin. */
@CoreIntegrationTest
class ApiSecurityTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void rejectsMissingInvalidAndBasicCredentials() throws Exception {
		mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsOnlyConfiguredCorsOrigin() throws Exception {
		mvc.perform(options("/api/company").header(HttpHeaders.ORIGIN, TestJwks.AUTHORIZED_PARTY)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TestJwks.AUTHORIZED_PARTY));
		mvc.perform(options("/api/company").header(HttpHeaders.ORIGIN, "https://other.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")).andExpect(status().isForbidden());
	}

}
