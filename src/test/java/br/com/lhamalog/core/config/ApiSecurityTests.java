package br.com.lhamalog.core.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.com.lhamalog.core.company.CompanyController;
import br.com.lhamalog.core.company.CompanyService;
import br.com.lhamalog.core.shared.CompanySessionResolver;

@WebMvcTest(CompanyController.class)
@Import({SecurityConfig.class, CompanySessionResolver.class})
@TestPropertySource(properties = "clerk.authorized-parties=http://localhost:5173")
class ApiSecurityTests {
	@Autowired MockMvc mvc;
	@MockitoBean CompanyService companyService;

	@Test
	void rejectsMissingInvalidAndBasicCredentials() throws Exception {
		mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").header("Authorization", "Bearer invalid"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company").header("Authorization", "Basic dXNlcjpwYXNz"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsOnlyConfiguredCorsOrigin() throws Exception {
		mvc.perform(options("/api/company").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mvc.perform(options("/api/company").header("Origin", "https://other.example")
				.header("Access-Control-Request-Method", "PUT")).andExpect(status().isForbidden());
	}
}
