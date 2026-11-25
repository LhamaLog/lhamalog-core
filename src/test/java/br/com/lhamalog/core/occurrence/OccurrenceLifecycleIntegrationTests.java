package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Spec 014 (LLT-136): change the status, archive and unarchive occurrences. */
@CoreIntegrationTest
class OccurrenceLifecycleIntegrationTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;

	/** A company with an admin, a product and occurrences written as the Occurrence Module would. */
	private final class Company {
		final String organization = "org_test_" + UUID.randomUUID();
		final TestTenants tenants = new TestTenants(mongo);
		final String tenantId = tenants.tenantId(organization);
		final String adminId = "admin_" + UUID.randomUUID();
		final RequestPostProcessor admin = tenants.session(organization, adminId, "admin");

		RequestPostProcessor member(String user) {
			return tenants.session(organization, user, "member");
		}

		String product(RequestPostProcessor actor) throws Exception {
			return json(mvc.perform(post("/api/products").with(actor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Produto " + UUID.randomUUID() + "\",\"context\":{}}"))
				.andExpect(status().isCreated())).get("id").asString();
		}

		String occurrence(String productId, String status) {
			return new OccurrenceFixtures(mongo).occurrence(tenantId, productId)
				.status(status)
				.seen(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-20T11:00:00Z"))
				.insert();
		}
	}

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode detail(RequestPostProcessor actor, String id) throws Exception {
		return json(mvc.perform(get("/api/occurrences/" + id).with(actor)).andExpect(status().isOk()));
	}

	private ResultActions changeStatus(RequestPostProcessor actor, String id, String status, String revision) throws Exception {
		return mvc.perform(put("/api/occurrences/" + id + "/status").with(actor).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(java.util.Map.of("status", status, "revision", revision))));
	}

	private ResultActions archive(RequestPostProcessor actor, String id, String action, String revision) throws Exception {
		return mvc.perform(post("/api/occurrences/" + id + "/" + action).with(actor).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(java.util.Map.of("revision", revision))));
	}

	private List<String> ids(RequestPostProcessor actor, String query) throws Exception {
		JsonNode page = json(mvc.perform(get("/api/occurrences" + query).with(actor)).andExpect(status().isOk()));
		List<String> ids = new ArrayList<>();
		page.get("items").forEach(item -> ids.add(item.get("id").asString()));
		return ids;
	}

	// US1

	@Test
	void changesTheStatusAndRecordsWhoAndWhen() throws Exception {
		Company company = new Company();
		String id = company.occurrence(company.product(company.admin), "ABERTA");
		JsonNode before = detail(company.admin, id);
		assertThat(before.get("revision").asString()).isEqualTo(id + "-r1");
		assertThat(before.get("archived").asBoolean()).isFalse();
		assertThat(before.get("archivedAt").isNull()).isTrue();

		JsonNode after = json(changeStatus(company.admin, id, "EM_ANALISE", id + "-r1").andExpect(status().isOk()));
		assertThat(after.get("status").asString()).isEqualTo("EM_ANALISE");
		assertThat(after.get("revision").asString()).isNotEqualTo(id + "-r1");
		JsonNode last = after.get("statusHistory").get(after.get("statusHistory").size() - 1);
		assertThat(last.get("from").asString()).isEqualTo("ABERTA");
		assertThat(last.get("to").asString()).isEqualTo("EM_ANALISE");
		assertThat(last.get("changedBy").get("type").asString()).isEqualTo("USER");
		assertThat(last.get("changedBy").get("id").asString()).isEqualTo(company.adminId);
		assertThat(Instant.parse(last.get("changedAt").asString())).isAfter(Instant.now().minusSeconds(60));

		assertThat(ids(company.admin, "?status=EM_ANALISE")).contains(id);
		assertThat(ids(company.admin, "?status=ABERTA")).doesNotContain(id);
	}

	@Test
	void reopensAClosedOccurrenceAndIgnoresTheSameStatus() throws Exception {
		Company company = new Company();
		String id = company.occurrence(company.product(company.admin), "FECHADA");
		JsonNode reopened = json(changeStatus(company.admin, id, "ABERTA", id + "-r1").andExpect(status().isOk()));
		assertThat(reopened.get("status").asString()).isEqualTo("ABERTA");
		int history = reopened.get("statusHistory").size();
		String revision = reopened.get("revision").asString();

		JsonNode same = json(changeStatus(company.admin, id, "ABERTA", revision).andExpect(status().isOk()));
		assertThat(same.get("statusHistory").size()).isEqualTo(history);
		assertThat(same.get("revision").asString()).isEqualTo(revision);
	}

	@Test
	void refusesUnknownStatusMissingRevisionAndStaleRevision() throws Exception {
		Company company = new Company();
		String id = company.occurrence(company.product(company.admin), "ABERTA");
		changeStatus(company.admin, id, "RESOLVIDA", id + "-r1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.status").value("Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA."));
		changeStatus(company.admin, id, "FECHADA", " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.revision").exists());
		changeStatus(company.admin, id, "FECHADA", "revisao-antiga")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("occurrence_changed"));
		assertThat(detail(company.admin, id).get("status").asString()).isEqualTo("ABERTA");
	}

	@Test
	void actionsFollowTheDetailVisibility() throws Exception {
		Company company = new Company();
		Company other = new Company();
		String id = company.occurrence(company.product(company.admin), "ABERTA");
		RequestPostProcessor outsider = company.member("member_" + UUID.randomUUID());
		changeStatus(outsider, id, "FECHADA", id + "-r1").andExpect(status().isNotFound());
		archive(outsider, id, "archive", id + "-r1").andExpect(status().isNotFound());
		changeStatus(other.admin, id, "FECHADA", id + "-r1").andExpect(status().isNotFound());
		changeStatus(company.admin, "nao-existe", "FECHADA", "x").andExpect(status().isNotFound());

		// A member with access to the product (the creator gets it) can act.
		RequestPostProcessor member = company.member("member_" + UUID.randomUUID());
		String own = company.occurrence(company.product(member), "ABERTA");
		changeStatus(member, own, "FECHADA", own + "-r1").andExpect(status().isOk());
		archive(member, own, "archive", detail(member, own).get("revision").asString()).andExpect(status().isOk());
	}

	@Test
	void finderDeliveryWithTheRevisionFromBeforeTheChangeIsStale() throws Exception {
		InternalModuleWorld world = new InternalModuleWorld(mvc, mapper, mongo);
		InternalModuleWorld.Company company = world.company();
		String signature = InternalModuleWorld.signature("mudada pelo usuario");
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature)).build())
			.andExpect(status().isCreated());
		String consulted = world.json(world.call(InternalModuleWorld.LOOKUP,
				InternalModuleWorld.lookup(company, signature, "ERROR"))).get("estado").get("revisao").asString();
		String id = ids(company.admin(), "").getFirst();
		changeStatus(company.admin(), id, "EM_ANALISE", consulted).andExpect(status().isOk());
		world.call(InternalModuleWorld.DELIVERY, InternalModuleWorld.summary(company)
				.finding(InternalModuleWorld.finding(signature).count(9).revision(consulted)).build())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.codigo").value("REVISAO_DESATUALIZADA"));
		assertThat(detail(company.admin(), id).get("status").asString()).isEqualTo("EM_ANALISE");
	}

	// US2

	@Test
	void archivedOccurrencesLeaveTheDefaultListAndComeBack() throws Exception {
		Company company = new Company();
		String product = company.product(company.admin);
		String kept = company.occurrence(product, "ABERTA");
		String id = company.occurrence(product, "EM_ANALISE");
		long total = json(mvc.perform(get("/api/occurrences").with(company.admin))).get("totalItems").asLong();

		JsonNode archived = json(archive(company.admin, id, "archive", id + "-r1").andExpect(status().isOk()));
		assertThat(archived.get("archived").asBoolean()).isTrue();
		assertThat(archived.get("status").asString()).isEqualTo("EM_ANALISE");
		assertThat(archived.get("archivedBy").get("type").asString()).isEqualTo("USER");
		assertThat(archived.get("archivedBy").get("id").asString()).isEqualTo(company.adminId);
		assertThat(archived.get("archivedAt").isNull()).isFalse();

		JsonNode page = json(mvc.perform(get("/api/occurrences").with(company.admin)));
		assertThat(page.get("totalItems").asLong()).isEqualTo(total - 1);
		assertThat(ids(company.admin, "")).containsExactly(kept);
		assertThat(ids(company.admin, "?archived=true")).containsExactly(id);
		assertThat(ids(company.admin, "?archived=true&status=ABERTA")).isEmpty();
		assertThat(json(mvc.perform(get("/api/occurrences?archived=true").with(company.admin)))
			.get("items").get(0).get("archived").asBoolean()).isTrue();
		mvc.perform(get("/api/occurrences?archived=talvez").with(company.admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.archived").value("Use true ou false."));
		assertThat(detail(company.admin, id).get("archived").asBoolean()).isTrue();

		// Archiving again changes nothing.
		String revision = archived.get("revision").asString();
		assertThat(json(archive(company.admin, id, "archive", revision).andExpect(status().isOk()))
			.get("revision").asString()).isEqualTo(revision);
		archive(company.admin, id, "unarchive", "revisao-antiga").andExpect(status().isConflict());

		JsonNode back = json(archive(company.admin, id, "unarchive", revision).andExpect(status().isOk()));
		assertThat(back.get("archived").asBoolean()).isFalse();
		assertThat(back.get("archivedAt").isNull()).isTrue();
		assertThat(back.get("status").asString()).isEqualTo("EM_ANALISE");
		assertThat(ids(company.admin, "")).containsExactlyInAnyOrder(kept, id);
		assertThat(json(archive(company.admin, id, "unarchive", back.get("revision").asString()).andExpect(status().isOk()))
			.get("revision").asString()).isEqualTo(back.get("revision").asString());
	}

	@Test
	void accessMatrixHasTheNewPermissionForTheThreeRoles() throws Exception {
		Company company = new Company();
		JsonNode access = json(mvc.perform(get("/api/company/access").with(company.admin)).andExpect(status().isOk()));
		List<String> roles = new ArrayList<>();
		access.get("matrix").forEach(row -> {
			if (row.get("permission").asString().equals("OCCURRENCES_MANAGE")) row.get("roles").forEach(r -> roles.add(r.asString()));
		});
		assertThat(roles).containsExactlyInAnyOrder("OWNER", "ADMIN", "MEMBER");
	}

}
