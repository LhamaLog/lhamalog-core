package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Spec 013, US1: {@code POST /internal/v1/ocorrencias/consultas}. */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OccurrenceLookupIntegrationTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	private InternalModuleWorld world() {
		return new InternalModuleWorld(mvc, mapper, mongo);
	}

	@Test
	void unknownKeyHasNoState() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		JsonNode answer = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, "ERROR|nada|svc|h", "ERROR"))
			.andExpect(status().isOk()));
		assertThat(answer.has("estado")).isTrue();
		assertThat(answer.get("estado").isNull()).isTrue();
	}

	@Test
	void storedOccurrenceAnswersCountDatesStatusAndRevision() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		String signature = "ERROR|timeout ao gravar|svc|h";
		String id = new OccurrenceFixtures(mongo).occurrence(company.tenantId(), company.productId())
			.signature(signature)
			.count(13)
			.seen(Instant.parse("2026-11-19T15:00:00Z"), Instant.parse("2026-11-19T15:05:00.250Z"))
			.status("EM_ANALISE")
			.insert();
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, signature, "ERROR"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado.contagemAcumulada").value(13))
			.andExpect(jsonPath("$.estado.primeiraOcorrencia").value("2026-11-19T15:00:00Z"))
			.andExpect(jsonPath("$.estado.ultimaOcorrencia").value("2026-11-19T15:05:00.250Z"))
			.andExpect(jsonPath("$.estado.status").value("EM_ANALISE"))
			.andExpect(jsonPath("$.estado.revisao").value(id + "-r1"));
		// Same signature with another type is another key.
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, signature, "WARNING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").isEmpty());
	}

	@Test
	void occurrenceWithoutRevisionAnswersTheFixedValue() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		String signature = "ERROR|sem revisao|svc|h";
		String id = new OccurrenceFixtures(mongo).occurrence(company.tenantId(), company.productId())
			.signature(signature)
			.insert();
		mongo.getCollection("Ocorrencia").updateOne(new Document("_id", id), new Document("$unset", new Document("revision", "")));
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, signature, "ERROR"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado.revisao").value("sem-revisao"));
	}

	@Test
	void invalidBodiesAreRefusedWithTheContractCodes() throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		Map<String, Object> extra = InternalModuleWorld.lookup(company, "ERROR|x|svc|h", "ERROR");
		extra.put("outro", "campo");
		for (Object body : new Object[] { extra, InternalModuleWorld.lookup(company, "ERROR|x|svc|h", "FATAL"),
				InternalModuleWorld.lookup(company, " ", "ERROR"), Map.of("tenantId", company.tenantId()) }) {
			world.call(InternalModuleWorld.LOOKUP, body)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
		}
		for (String body : new String[] { "{nao e json", "[]" }) {
			world.call(InternalModuleWorld.LOOKUP, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
		}
	}

	@Test
	void occurrenceWithoutCountIsAnInternalErrorWithoutItsContent(CapturedOutput output) throws Exception {
		InternalModuleWorld world = world();
		InternalModuleWorld.Company company = world.company();
		String signature = "ERROR|conteudo-que-nao-pode-vazar|svc|h";
		String id = new OccurrenceFixtures(mongo).occurrence(company.tenantId(), company.productId())
			.signature(signature)
			.insert();
		mongo.getCollection("Ocorrencia").updateOne(new Document("_id", id), new Document("$unset", new Document("count", "")));
		world.call(InternalModuleWorld.LOOKUP, InternalModuleWorld.lookup(company, signature, "ERROR"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.codigo").value("OCORRENCIA_INVALIDA"));
		assertThat(output).contains("event=occurrence.lookup.invalid").doesNotContain("conteudo-que-nao-pode-vazar");
	}

}
