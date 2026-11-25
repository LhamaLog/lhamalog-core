package br.com.lhamalog.core.member;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.RecordingSessionRevocation;
import br.com.lhamalog.core.support.TestTenants;

/**
 * Acceptance tests for the LLT-119 validation (spec 012). They cover the gaps left by {@link CompanyMemberHttpTests}
 * against the company-members contract of LLT-168 as it is on Development. Test code only: no production change.
 * Since LLT-163 the requests carry real signed session tokens and the member records use the internal tenant id.
 */
@CoreIntegrationTest
class CompanyMemberAcceptanceTests {
	private static final String DEACTIVATED = "Seu acesso a esta empresa foi desativado.";
	private static final String LAST_ADMIN = "Não é possível desativar o último administrador.";
	private static final Instant START = Instant.parse("2026-11-16T12:00:00Z");

	@Autowired MockMvc mvc;
	@Autowired MemberClock clock;
	@Autowired MongoTemplate mongo;
	@Autowired CompanyMemberRepository members;
	@Autowired CompanyAdminCountRepository adminCounts;
	@Autowired RecordingSessionRevocation revocation;
	private TestTenants tenants;

	@BeforeEach
	void resetClock() {
		clock.reset();
		revocation.reset();
	}

	@AfterEach
	void restoreClock() {
		clock.reset();
		revocation.reset();
	}

	/** AT-01 (AC-10): a request refused for deactivation keeps last access and observed role untouched. */
	@Test
	void deactivatedRequestKeepsLastAccessAndDoesNotPromoteRole() throws Exception {
		String organization = organization();
		clock.setClock(Clock.fixed(START, ZoneOffset.UTC));
		touch(current(organization, "user_admin", "admin"));
		touch(current(organization, "user_m", "member"));
		mvc.perform(post("/api/company/members/user_m/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isOk());
		String before = lastAccess(organization, "user_admin", "user_m");
		clock.setClock(Clock.fixed(START.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
		// Admin role in the token: if the Core recorded it, user_m would become an active administrator.
		mvc.perform(get("/api/products").with(current(organization, "user_m", "admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("user_deactivated"))
				.andExpect(jsonPath("$.message").value(DEACTIVATED));
		list(organization, "user_admin")
				.andExpect(jsonPath(accessOf("user_m")).value(hasItem(before)))
				.andExpect(jsonPath(statusOf("user_m")).value(hasItem("deactivated")));
		String tenantId = tenants().tenantId(organization);
		CompanyMember stored = members.findById(CompanyMember.idOf(tenantId, "user_m")).orElseThrow();
		assertFalse(stored.administrator(), "refused request must not record the admin role");
		assertEquals(START, stored.lastAccessAt(), "refused request must not record last access");
		// user_admin is still the only active administrator the Core knows.
		mvc.perform(post("/api/company/members/user_admin/deactivation").with(current(organization, "user_admin", "admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("last_admin"))
				.andExpect(jsonPath("$.message").value(LAST_ADMIN));
	}

	/** AT-02 (AC-18): a deactivated administrator loses member management too. */
	@Test
	void deactivatedAdministratorLosesMemberManagement() throws Exception {
		String organization = organization();
		touch(current(organization, "user_a", "admin"));
		touch(current(organization, "user_b", "admin"));
		mvc.perform(post("/api/company/members/user_a/deactivation").with(current(organization, "user_b", "admin")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/company/members").with(current(organization, "user_a", "admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("user_deactivated"))
				.andExpect(jsonPath("$.message").value(DEACTIVATED));
		mvc.perform(delete("/api/company/members/user_a/deactivation").with(current(organization, "user_a", "admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("user_deactivated"));
		mvc.perform(post("/api/company/members/user_b/deactivation").with(current(organization, "user_a", "admin")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("user_deactivated"));
		list(organization, "user_b")
				.andExpect(jsonPath(statusOf("user_a")).value(hasItem("deactivated")))
				.andExpect(jsonPath(statusOf("user_b")).value(hasItem("active")));
	}

	/**
	 * AT-03 (AC-08): write routes answer 401 without a session. Since LLT-163 a valid session without an active
	 * organization is refused by the security chain with 403 {@code {"error":"forbidden"}} and the user's sessions are
	 * revoked, instead of the old "Selecione uma empresa para continuar." message.
	 */
	@Test
	void writeRoutesRequireSessionAndCompany() throws Exception {
		mvc.perform(post("/api/company/members/user_x/deactivation")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/company/members/user_x/deactivation")).andExpect(status().isUnauthorized());
		RequestPostProcessor noCompany = TestTenants.withoutOrganization("user_z");
		mvc.perform(post("/api/company/members/user_x/deactivation").with(noCompany))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
		mvc.perform(delete("/api/company/members/user_x/deactivation").with(noCompany))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
		assertTrue(revocation.revokedUsers().contains("user_z"), "a session without company must be revoked");
	}

	/**
	 * AT-04 (R-01, investigation): two administrators reach the Core for the first time at once. The admin counter
	 * must match the real count, and deactivating both in sequence must keep one.
	 */
	@Tag("investigacao")
	@RepeatedTest(20)
	void firstAccessRaceKeepsCounterAndOneAdministrator() throws Exception {
		String organization = organization();
		// Create the tenant before the race, so both threads only compete on the member records.
		String tenantId = tenants().tenantId(organization);
		RequestPostProcessor userA = current(organization, "user_a", "admin");
		RequestPostProcessor userB = current(organization, "user_b", "admin");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = pool.submit(() -> firstAccess(userA, ready, start));
			Future<Integer> second = pool.submit(() -> firstAccess(userB, ready, start));
			ready.await();
			start.countDown();
			assertEquals(200, first.get(), "first access of user_a");
			assertEquals(200, second.get(), "first access of user_b");
		}
		finally {
			pool.shutdownNow();
		}
		long known = members.countByTenantIdAndAdministratorTrueAndStatus(tenantId, CompanyMember.ACTIVE);
		long counted = adminCounts.findById(tenantId).map(CompanyAdminCount::activeAdminCount).orElse(-1L);
		assertEquals(2, known, "active administrators recorded");
		assertEquals(known, counted, "admin counter differs from the recorded administrators (R-01)");
		mvc.perform(post("/api/company/members/user_b/deactivation").with(userA))
				.andExpect(status().isOk());
		mvc.perform(post("/api/company/members/user_a/deactivation").with(userA))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("last_admin"));
		assertEquals(1, members.countByTenantIdAndAdministratorTrueAndStatus(tenantId, CompanyMember.ACTIVE),
				"one active administrator must remain");
	}

	private int firstAccess(RequestPostProcessor actor, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await();
		return mvc.perform(get("/api/products").with(actor)).andReturn().getResponse().getStatus();
	}

	// Helpers copied from CompanyMemberHttpTests (private there).

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
