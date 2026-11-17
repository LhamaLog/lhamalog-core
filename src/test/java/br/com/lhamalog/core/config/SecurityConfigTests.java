package br.com.lhamalog.core.config;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestJwks;

@CoreIntegrationTest
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void infoIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
	}

	@Test
	void otherEndpointsRequireAuthenticationWithBasicBody() throws Exception {
		mockMvc.perform(get("/api/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().json("{\"error\":\"unauthorized\"}", true));
	}

	@Test
	void clerkWebhookDoesNotRequireSessionToken() throws Exception {
		mockMvc.perform(post("/webhooks/clerk").content("{}")).andExpect(status().is(not(401)));
	}

	@Test
	void httpBasicIsDisabled() throws Exception {
		mockMvc.perform(get("/api/anything").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA=="))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void corsPreflightFromAuthorizedOriginIsAllowed() throws Exception {
		mockMvc.perform(options("/api/anything").header(HttpHeaders.ORIGIN, TestJwks.AUTHORIZED_PARTY)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TestJwks.AUTHORIZED_PARTY));
	}

	@Test
	void corsPreflightFromOtherOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/anything").header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
