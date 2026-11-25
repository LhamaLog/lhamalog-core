package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;

import br.com.lhamalog.core.config.InternalServiceSecurityConfigurationAccess;
import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestJwks;
import tools.jackson.databind.ObjectMapper;

/** Spec 013, US4: only the Finder with the internal JWT reaches the Occurrence Module routes. */
@CoreIntegrationTest
class InternalAuthIntegrationTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	private InternalModuleWorld world() {
		return new InternalModuleWorld(mvc, mapper, mongo);
	}

	@Test
	void refusesCallsWithoutAValidInternalToken() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		Object body = InternalModuleWorld.lookup(company, "ERROR|x|svc|h", "ERROR");
		Instant now = Instant.now();
		List<String> refused = List.of(
				"Bearer nao-e-um-jwt",
				"Bearer " + InternalTokens.finder().secret(InternalTokens.OTHER_SECRET).sign(),
				"Bearer " + InternalTokens.finder().algorithm(JWSAlgorithm.HS512).sign(),
				"Bearer " + InternalTokens.finder().claim("exp", now.minusSeconds(120)).sign(),
				"Bearer " + InternalTokens.finder().claim("nbf", now.plusSeconds(600)).sign(),
				"Bearer " + InternalTokens.finder().claim("iss", "outro-emissor").sign(),
				"Bearer " + InternalTokens.finder().claim("aud", List.of("outra-audiencia")).sign(),
				"Bearer " + InternalTokens.finder().claim("sub", null).sign(),
				TestJwks.token().organization(company.organization(), "admin").subject("user_1").bearer(),
				"Basic dXNlcjpwYXNz");
		for (String authorization : refused) {
			world.call(InternalModuleWorld.LOOKUP, body, authorization)
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
		}
		world.call(InternalModuleWorld.LOOKUP, body, null).andExpect(status().isUnauthorized());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company).build(), null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void anotherServiceWithAValidTokenIsForbidden() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, "ERROR|x|svc|h", "ERROR"),
				"Bearer " + InternalTokens.finder().claim("sub", "lhamalog-varredura").sign())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
	}

	@Test
	void acceptsTheCurrentAndThePreviousSecret() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		Object body = InternalModuleWorld.lookup(company, "ERROR|x|svc|h", "ERROR");
		world.call(InternalModuleWorld.LOOKUP, body).andExpect(status().isOk());
		world.call(InternalModuleWorld.LOOKUP, body,
				"Bearer " + InternalTokens.finder().secret(InternalTokens.PREVIOUS_SECRET).sign())
			.andExpect(status().isOk());
	}

	@Test
	void productOfAnotherTenantIsForbiddenWithoutSayingWhy() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		InternalModuleWorld.Company other = world.company();
		var foreign = new InternalModuleWorld.Company(company.organization(), company.tenantId(), other.productId(),
				company.admin());
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(foreign, "ERROR|x|svc|h", "ERROR"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
		var unknown = new InternalModuleWorld.Company(company.organization(), "tenant-" + UUID.randomUUID(),
				company.productId(), company.admin());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(unknown).build())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
	}

	@Test
	void internalTokenDoesNotOpenTheUserRoutes() throws Exception {
		mvc.perform(get("/api/occurrences").header(HttpHeaders.AUTHORIZATION, InternalTokens.bearer()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void withoutSecretsEveryTokenIsRefused() {
		var decoder = InternalServiceSecurityConfigurationAccess.decoder(
				new OccurrenceModuleProperties.InternalAuth(List.of(), null, null, null));
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
				() -> decoder.decode(InternalTokens.finder().sign()))).isNotNull();
	}

	@Test
	void secretsShorterThan32BytesAreIgnored() {
		var decoder = InternalServiceSecurityConfigurationAccess.decoder(
				new OccurrenceModuleProperties.InternalAuth(List.of("curto"), null, null, null));
		org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
				() -> decoder.decode(InternalTokens.finder().secret("curto-curto-curto-curto-curto-curto").sign()));
	}

}
