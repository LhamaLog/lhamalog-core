package br.com.lhamalog.core.occurrence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Test helper of the Occurrence Module (spec 013): a company with a product created through the API, calls of the
 * internal routes with the internal JWT, and builders of the contract bodies.
 */
final class InternalModuleWorld {

	static final String LOOKUP = "/internal/v1/ocorrencias/consultas";

	static final String DELIVERY = "/internal/v1/resumos-analise";

	private final MockMvc mvc;

	private final ObjectMapper mapper;

	private final TestTenants tenants;

	InternalModuleWorld(MockMvc mvc, ObjectMapper mapper, MongoTemplate mongo) {
		this.mvc = mvc;
		this.mapper = mapper;
		this.tenants = new TestTenants(mongo);
	}

	/** Organization, tenant, admin session and one product. */
	Company company() throws Exception {
		String organization = "org_test_" + UUID.randomUUID();
		String tenantId = tenants.tenantId(organization);
		RequestPostProcessor admin = tenants.session(organization, "admin_" + UUID.randomUUID(), "admin");
		String productId = mapper.readTree(mvc.perform(post("/api/products").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto " + UUID.randomUUID() + "\",\"context\":{}}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("id").asString();
		return new Company(organization, tenantId, productId, admin);
	}

	record Company(String organization, String tenantId, String productId, RequestPostProcessor admin) {}

	ResultActions call(String path, Object body) throws Exception {
		return call(path, body, InternalTokens.bearer());
	}

	ResultActions call(String path, Object body, String authorization) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON)
			.content(body instanceof String text ? text : mapper.writeValueAsString(body));
		if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
		return mvc.perform(request);
	}

	JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	static Map<String, Object> lookup(Company company, String signature, String type) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("tenantId", company.tenantId());
		body.put("productId", company.productId());
		body.put("assinatura", signature);
		body.put("tipo", type);
		return body;
	}

	static Summary summary(Company company) {
		return new Summary(company.tenantId(), company.productId());
	}

	/** {@code ResumoAnalise} builder; counters always consistent with the findings. */
	static final class Summary {

		private final Map<String, Object> body = new LinkedHashMap<>();

		private final List<Map<String, Object>> findings = new ArrayList<>();

		Summary(String tenantId, String productId) {
			body.put("tenantId", tenantId);
			body.put("productId", productId);
			body.put("batchId", "batch-" + UUID.randomUUID());
			body.put("versaoRegras", "regras-v1");
			body.put("achados", findings);
		}

		Summary batch(String batchId) {
			body.put("batchId", batchId);
			return this;
		}

		Summary exec(String execId) {
			body.put("execId", execId);
			return this;
		}

		Summary rules(String version) {
			body.put("versaoRegras", version);
			return this;
		}

		Summary set(String field, Object value) {
			body.put(field, value);
			return this;
		}

		Summary finding(Finding finding) {
			findings.add(finding.body);
			return this;
		}

		Map<String, Object> build() {
			long errors = findings.stream().filter(f -> "ERROR".equals(f.get("tipo"))).count();
			long warnings = findings.stream().filter(f -> "WARNING".equals(f.get("tipo"))).count();
			long info = findings.size() - errors - warnings;
			body.putIfAbsent("contadores", new LinkedHashMap<>(Map.of("linhasProcessadas", errors + warnings + info,
					"erros", errors, "warnings", warnings, "informacao", info, "dadosSensiveisMascarados", 0)));
			return body;
		}

	}

	static Finding finding(String signature) {
		return new Finding(signature);
	}

	/** {@code AchadoAnalise} builder with every required field. */
	static final class Finding {

		final Map<String, Object> body = new LinkedHashMap<>();

		Finding(String signature) {
			body.put("tipo", "ERROR");
			body.put("assinatura", signature);
			body.put("hostName", "host-1");
			body.put("contagem", 3);
			body.put("primeiraOcorrencia", "2026-11-20T10:00:00Z");
			body.put("ultimaOcorrencia", "2026-11-20T10:05:00Z");
			body.put("recorrente", false);
			body.put("linhas", List.of("linha 1 <EMAIL>", "linha 2"));
			body.put("revisaoConsultada", null);
			body.put("titulo", null);
			body.put("resumo", null);
			body.put("numeroLinha", null);
			body.put("camposExtraido", null);
			body.put("dadosSensiveis", null);
		}

		Finding type(String type) {
			body.put("tipo", type);
			return this;
		}

		Finding count(long count) {
			body.put("contagem", count);
			return this;
		}

		Finding seen(Instant first, Instant last) {
			body.put("primeiraOcorrencia", first.toString());
			body.put("ultimaOcorrencia", last.toString());
			return this;
		}

		Finding revision(String revision) {
			body.put("revisaoConsultada", revision);
			return this;
		}

		Finding set(String field, Object value) {
			body.put(field, value);
			return this;
		}

		Finding without(String field) {
			body.remove(field);
			return this;
		}

	}

	static String signature(String message) {
		return "ERROR|" + message + "|svc|host-1";
	}

}
