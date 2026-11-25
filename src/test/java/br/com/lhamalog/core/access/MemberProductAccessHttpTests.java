package br.com.lhamalog.core.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Feature 011 (LLT-120), US2: the owner or an administrator chooses the products each member can access. */
@CoreIntegrationTest
class MemberProductAccessHttpTests {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@Test
	void releasedProductsAreTheOnlyOnesTheMemberSeesAndRemovingOneHidesIt() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		var member = session(org, "user_member", "member");
		String a = product(admin, "Produto A");
		String b = product(admin, "Produto B");
		String c = product(admin, "Produto C");

		replace(admin, "user_member", a, b).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user_member"))
				.andExpect(jsonPath("$.allProducts").value(false))
				.andExpect(jsonPath("$.productIds.length()").value(2));
		mvc.perform(get("/api/company/members/user_member/products").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.productIds.length()").value(2));
		assertEquals(sorted(a, b), productIdsSeenBy(member));
		mvc.perform(get("/api/products/" + c).with(member)).andExpect(status().isNotFound());

		replace(admin, "user_member", a).andExpect(status().isOk()).andExpect(jsonPath("$.productIds[0]").value(a));
		assertEquals(List.of(a), productIdsSeenBy(member));
		mvc.perform(get("/api/products/" + b).with(member)).andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + a).with(member)).andExpect(status().isOk());
	}

	@Test
	void productOfAnotherCompanyIsRefusedAndNothingChanges() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		String mine = product(admin, "Meu produto");
		String foreign = product(session(organization(), "user_x", "admin"), "Produto de fora");
		replace(admin, "user_member", mine).andExpect(status().isOk());

		replace(admin, "user_member", mine, foreign).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("product_not_found"))
				.andExpect(jsonPath("$.message").value("Produto não encontrado nesta empresa."));
		replace(admin, "user_member", "does-not-exist").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("product_not_found"));
		assertEquals(List.of(mine), storedProductIds(org, "user_member"));
	}

	@Test
	void ownerAndKnownAdministratorsHaveFullAccessAndCannotBeRestricted() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		String a = product(admin, "Produto A");
		touch(session(org, "user_other_admin", "admin"));
		mongo.insert(new MemberDocument(UUID.randomUUID().toString(), tenants().tenantId(org), "user_owner", null, "DONO",
				null, null, 1, 0, null, null));
		mongo.insert(new MemberDocument(UUID.randomUUID().toString(), tenants().tenantId(org), "user_synced_admin", null,
				"ADMIN", null, null, 1, 0, null, null));

		for (String target : List.of("user_other_admin", "user_owner", "user_synced_admin")) {
			replace(admin, target, a).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("full_access"))
					.andExpect(jsonPath("$.message").value("Este membro acessa todos os Produtos."));
			mvc.perform(get("/api/company/members/" + target + "/products").with(admin)).andExpect(status().isOk())
					.andExpect(jsonPath("$.allProducts").value(true))
					.andExpect(jsonPath("$.productIds.length()").value(0));
		}
	}

	@Test
	void membersCannotReadOrChangeAccessesAndKeepTheSession() throws Exception {
		String org = organization();
		var member = session(org, "user_member", "member");
		mvc.perform(get("/api/company/members/user_member/products").with(member)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"));
		replace(member, "user_member").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
	}

	@Test
	void sameListTwiceGivesTheSameResultWithoutDuplicates() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		String a = product(admin, "Produto A");
		String b = product(admin, "Produto B");
		replace(admin, "user_member", a, b, a).andExpect(status().isOk()).andExpect(jsonPath("$.productIds.length()").value(2));
		replace(admin, "user_member", b, a).andExpect(status().isOk()).andExpect(jsonPath("$.productIds.length()").value(2));
		assertEquals(sorted(a, b), storedProductIds(org, "user_member"));

		replace(admin, "user_member").andExpect(status().isOk()).andExpect(jsonPath("$.productIds.length()").value(0));
		assertEquals(List.of(), storedProductIds(org, "user_member"));
	}

	@Test
	void archivedProductCanBeReleased() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		JsonNode created = json(mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Arquivado\",\"context\":{}}")).andExpect(status().isCreated()));
		String id = created.get("id").asText();
		mvc.perform(post("/api/products/" + id + "/archive").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":" + created.get("version").asLong() + "}")).andExpect(status().isOk());
		replace(admin, "user_member", id).andExpect(status().isOk()).andExpect(jsonPath("$.productIds[0]").value(id));
	}

	@Test
	void invalidInputIsRefused() throws Exception {
		String org = organization();
		var admin = session(org, "user_admin", "admin");
		String tooMany = IntStream.range(0, 501).mapToObj((i) -> "\"p" + i + "\"").collect(Collectors.joining(","));
		mvc.perform(put("/api/company/members/user_member/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productIds\":[" + tooMany + "]}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.productIds").exists());
		mvc.perform(put("/api/company/members/user_member/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.productIds").exists());
		String longUser = "u".repeat(201);
		mvc.perform(get("/api/company/members/" + longUser + "/products").with(admin)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Informe o usuário."));
		replace(admin, longUser).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Informe o usuário."));
	}

	private ResultActions replace(RequestPostProcessor actor, String userId, String... productIds) throws Exception {
		String ids = java.util.Arrays.stream(productIds).map((id) -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mvc.perform(put("/api/company/members/" + userId + "/products").with(actor)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[" + ids + "]}"));
	}

	private String product(RequestPostProcessor admin, String name) throws Exception {
		return json(mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"context\":{}}")).andExpect(status().isCreated())).get("id").asText();
	}

	private List<String> productIdsSeenBy(RequestPostProcessor member) throws Exception {
		JsonNode list = json(mvc.perform(get("/api/products").with(member)).andExpect(status().isOk()));
		return java.util.stream.StreamSupport.stream(list.spliterator(), false).map((p) -> p.get("id").asText()).sorted().toList();
	}

	private List<String> storedProductIds(String org, String userId) {
		return mongo.find(Query.query(Criteria.where("tenantId").is(tenants().tenantId(org)).and("clerkUserId").is(userId)),
				ProductAccess.class).stream().map(ProductAccess::productId).sorted().toList();
	}

	private void touch(RequestPostProcessor actor) throws Exception {
		mvc.perform(get("/api/products").with(actor)).andExpect(status().isOk());
	}

	private static List<String> sorted(String... ids) {
		return java.util.Arrays.stream(ids).sorted().toList();
	}

	private JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private RequestPostProcessor session(String org, String user, String role) {
		return tenants().session(org, user, role);
	}

	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private String organization() {
		return "org_pa_" + UUID.randomUUID();
	}
}
