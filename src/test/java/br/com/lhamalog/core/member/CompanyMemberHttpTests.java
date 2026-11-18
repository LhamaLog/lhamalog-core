package br.com.lhamalog.core.member;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;


@CoreIntegrationTest
class CompanyMemberHttpTests {
	private static final String NOT_ADMIN = "Somente o administrador da empresa pode gerenciar os usuários.";
	private static final String DEACTIVATED = "Seu acesso a esta empresa foi desativado.";
	private static final String LAST_ADMIN = "Não é possível desativar o último administrador.";
	private static final String USER_REQUIRED = "Informe o usuário.";
	private static final Instant START = Instant.parse("2026-11-15T12:00:00Z");

	@Autowired MockMvc mvc;
	@Autowired MemberClock clock;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	@AfterEach
	void restoreClock() {
		clock.reset();
	}

	@Test
	void listsMembersForTheAdministratorOfTheCompany() throws Exception {
		String organization = organization();
		String other = organization();
		touch(current(organization, "user_b", "admin"));
		touch(current(organization, "user_a", "member"));
		touch(current(other, "user_c", "member"));
		mvc.perform(get("/api/company/members").with(current(organization, "user_b", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[0].userId").value("user_a"))
				.andExpect(jsonPath("$.members[1].userId").value("user_b"))
				.andExpect(jsonPath("$.members[?(@.userId=='user_c')]").isEmpty())
				.andExpect(jsonPath("$.members[0].name").doesNotExist())
				.andExpect(jsonPath("$.members[0].email").doesNotExist())
				.andExpect(jsonPath("$.members[0].role").doesNotExist());
		mvc.perform(get("/api/company/members").with(current(organization, "user_a", "member")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"))
				.andExpect(jsonPath("$.message").value(NOT_ADMIN));
		mvc.perform(get("/api/company/members")).andExpect(status().isUnauthorized());
		// Without an active organization the security chain answers 403 and revokes the sessions (LLT-163).
		mvc.perform(get("/api/company/members").with(TestTenants.withoutOrganization("user_z")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	@Test
	void keepsLastAccessUntilFiveMinutesPass() throws Exception {
		String organization = organization();
		clock.setClock(Clock.fixed(START, ZoneOffset.UTC));
		touch(current(organization, "user_admin", "admin"));
		touch(current(organization, "user_m", "member"));
		String first = lastAccess(organization, "user_admin", "user_m");
		clock.setClock(Clock.fixed(START.plus(Duration.ofMinutes(1)), ZoneOffset.UTC));
		touch(current(organization, "user_m", "member"));
		list(organization, "user_admin").andExpect(jsonPath(accessOf("user_m")).value(hasItem(first)));
		clock.setClock(Clock.fixed(START.plus(Duration.ofMinutes(5)), ZoneOffset.UTC));
		touch(current(organization, "user_m", "member"));
		list(organization, "user_admin").andExpect(jsonPath(accessOf("user_m")).value(not(hasItem(first))));
	}

	@Test
	void deactivatesAndReactivatesWithoutLeavingTheCompany() throws Exception {
		String organization = organization();
		String elsewhere = organization();
		touch(current(organization, "user_admin", "admin"));
		touch(current(organization, "user_m", "member"));
		String access = lastAccess(organization, "user_admin", "user_m");
		mvc.perform(post("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("deactivated"))
				.andExpect(jsonPath("$.lastAccessAt").value(access));
		for (String path : new String[] {"/api/company", "/api/products", "/api/user/profile"}) {
			mvc.perform(get(path).with(current(organization, "user_m", "member")))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("user_deactivated"))
					.andExpect(jsonPath("$.message").value(DEACTIVATED));
		}
		mvc.perform(get("/api/products").with(current(organization, "user_admin", "admin"))).andExpect(status().isOk());
		mvc.perform(post("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("deactivated"));
		mvc.perform(delete("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
		mvc.perform(get("/api/user/profile").with(current(organization, "user_m", "member"))).andExpect(status().isOk());
		mvc.perform(delete("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
		mvc.perform(post("/api/company/members/user_admin/deactivation").with(current(organization, "user_m", "member")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(delete("/api/company/members/user_admin/deactivation").with(current(organization, "user_m", "member")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(post("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/company").with(current(elsewhere, "user_m", "member"))).andExpect(status().isOk());
		String tooLong = "u".repeat(201);
		mvc.perform(post("/api/company/members/" + tooLong + "/deactivation")
				.with(current(organization, "user_admin", "admin")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(USER_REQUIRED))
				.andExpect(jsonPath("$.code").doesNotExist());
		list(organization, "user_admin").andExpect(jsonPath("$.members[?(@.userId=='" + tooLong + "')]").isEmpty());
		mvc.perform(post("/api/company/members/user_new/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("deactivated"))
				.andExpect(jsonPath("$.lastAccessAt").value(nullValue()));
	}

	@Test
	void refusesToDeactivateTheLastKnownActiveAdministrator() throws Exception {
		String organization = organization();
		touch(current(organization, "user_a", "admin"));
		touch(current(organization, "user_b", "org:admin"));
		mvc.perform(post("/api/company/members/user_a/deactivation").with(current(organization, "user_b", "org:admin")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("deactivated"));
		mvc.perform(post("/api/company/members/user_b/deactivation").with(current(organization, "user_b", "org:admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("last_admin"))
				.andExpect(jsonPath("$.message").value(LAST_ADMIN));
		list(organization, "user_b").andExpect(jsonPath(statusOf("user_b")).value(hasItem("active")));
		String pair = organization();
		touch(current(pair, "user_a", "admin"));
		touch(current(pair, "user_b", "admin"));
		mvc.perform(post("/api/company/members/user_a/deactivation").with(current(pair, "user_a", "admin")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/products").with(current(pair, "user_a", "admin")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("user_deactivated"));
		mvc.perform(get("/api/company/members").with(current(pair, "user_b", "admin")))
				.andExpect(status().isOk()).andExpect(jsonPath(statusOf("user_b")).value(hasItem("active")));
		String guarded = organization();
		touch(current(guarded, "user_admin", "org:admin"));
		touch(current(guarded, "user_m", "member"));
		touch(current(guarded, "user_p", "org:member"));
		mvc.perform(get("/api/company/members").with(current(guarded, "user_p", "org:member")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_admin"));
		mvc.perform(post("/api/company/members/user_m/deactivation").with(current(guarded, "user_admin", "org:admin")))
				.andExpect(status().isOk());
		mvc.perform(post("/api/company/members/user_admin/deactivation").with(current(guarded, "user_admin", "org:admin")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("last_admin"));
	}

	@Test
	void simultaneousDeactivationKeepsOneActiveAdministrator() throws Exception {
		String organization = organization();
		touch(current(organization, "user_a", "admin"));
		touch(current(organization, "user_b", "admin"));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = pool.submit(() -> deactivate(organization, "user_a", "user_b", ready, start));
			Future<Integer> second = pool.submit(() -> deactivate(organization, "user_b", "user_a", ready, start));
			ready.await();
			start.countDown();
			int left = first.get();
			int right = second.get();
			if (left + right != 200 + 403) {
				throw new AssertionError("expected 200 and 403, got " + left + " and " + right);
			}
		}
		finally {
			pool.shutdownNow();
		}
		long active = members.countByTenantIdAndAdministratorTrueAndStatus(tenants().tenantId(organization), CompanyMember.ACTIVE);
		if (active != 1) {
			throw new AssertionError("expected one active administrator, got " + active);
		}
	}

	@Autowired CompanyMemberRepository members;

	@Test
	void acceptedTokenUpdatesRoleInsideTheIntervalAndRolePathStaysUnmapped() throws Exception {
		String organization = organization();
		clock.setClock(Clock.fixed(START, ZoneOffset.UTC));
		touch(current(organization, "user_a", "admin"));
		touch(current(organization, "user_m", "member"));
		String first = lastAccess(organization, "user_a", "user_m");
		clock.setClock(Clock.fixed(START.plus(Duration.ofMinutes(1)), ZoneOffset.UTC));
		touch(current(organization, "user_m", "admin"));
		list(organization, "user_a").andExpect(jsonPath(accessOf("user_m")).value(not(hasItem(first))));
		mvc.perform(post("/api/company/members/user_a/deactivation").with(current(organization, "user_m", "admin")))
				.andExpect(status().isOk());
		String swapped = organization();
		clock.setClock(Clock.fixed(START, ZoneOffset.UTC));
		touch(current(swapped, "user_a", "admin"));
		touch(current(swapped, "user_b", "admin"));
		clock.setClock(Clock.fixed(START.plus(Duration.ofMinutes(1)), ZoneOffset.UTC));
		touch(current(swapped, "user_b", "member"));
		mvc.perform(post("/api/company/members/user_a/deactivation").with(current(swapped, "user_a", "admin")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("last_admin"));
		list(swapped, "user_a").andExpect(jsonPath("$.members[0].role").doesNotExist());
		mvc.perform(post("/api/company/members/user_a/role").with(current(organization(), "user_role", "admin")))
				.andExpect(status().isNotFound());
	}

	private void touch(RequestPostProcessor actor) throws Exception {
		mvc.perform(get("/api/products").with(actor)).andExpect(status().isOk());
	}

	private ResultActions list(String organization, String reader) throws Exception {
		return mvc.perform(get("/api/company/members").with(current(organization, reader, "admin")));
	}

	private String lastAccess(String organization, String reader, String userId) throws Exception {
		var document = tools.jackson.databind.json.JsonMapper.shared()
				.readTree(list(organization, reader).andReturn().getResponse().getContentAsString());
		for (var member : document.get("members")) {
			if (userId.equals(member.get("userId").asString())) {
				return member.get("lastAccessAt").asString();
			}
		}
		throw new AssertionError("missing " + userId);
	}

	private static String accessOf(String userId) {
		return "$.members[?(@.userId=='" + userId + "')].lastAccessAt";
	}

	private static String statusOf(String userId) {
		return "$.members[?(@.userId=='" + userId + "')].status";
	}

	private int deactivate(String organization, String caller, String target, CountDownLatch ready, CountDownLatch start)
			throws Exception {
		ready.countDown();
		start.await();
		return mvc.perform(post("/api/company/members/" + target + "/deactivation")
				.with(current(organization, caller, "admin"))).andReturn().getResponse().getStatus();
	}

	private RequestPostProcessor current(String organization, String user, String role) {
		return tenants().session(organization, user, role);
	}

	/** Real tokens and an ACTIVE tenant per organization (LLT-163). */
	private TestTenants tenants() {
		if (tenants == null) {
			tenants = new TestTenants(mongo);
		}
		return tenants;
	}

	private String organization() {
		return "org_member_" + UUID.randomUUID();
	}
}
