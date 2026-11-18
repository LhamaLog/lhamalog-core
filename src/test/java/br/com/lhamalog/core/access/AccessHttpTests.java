package br.com.lhamalog.core.access;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestTenants;

/**
 * Feature 011 (LLT-120), US1: access role and permission matrix. The owner is the LLT-163 {@code member} with role
 * DONO; this feature only reads it (clarify C1, 18/11), so the tests write it straight into the collection, as the
 * purchase flow will.
 */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AccessHttpTests {
	static final String NOT_ALLOWED = "Somente o Dono ou um Administrador pode fazer esta ação.";

	@Autowired MockMvc mvc;
	@Autowired MongoTemplate mongo;
	private TestTenants tenants;

	@Test
	void ownerWithAnAdminTokenIsOwnerAndNothingIsWritten() throws Exception {
		String org = organization();
		String memberId = owner(org, "user_owner");
		Document before = memberDocument(memberId);

		mvc.perform(get("/api/company/access/me").with(session(org, "user_owner", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user_owner"))
				.andExpect(jsonPath("$.role").value("OWNER"))
				.andExpect(jsonPath("$.permissions").value(hasItem("SUBSCRIPTION_MANAGE")))
				.andExpect(jsonPath("$.permissions").value(hasItem("MEMBERS_INVITE_ADMIN")));

		assertEquals(before, memberDocument(memberId), "access/me must not change the member (SC-001)");
	}

	@Test
	void adminWithoutOwnerRecordIsAdminAndNoOwnerIsCreated() throws Exception {
		String org = organization();
		mvc.perform(get("/api/company/access/me").with(session(org, "user_admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.permissions").value(hasItem("MEMBERS_VIEW")))
				.andExpect(jsonPath("$.permissions").value(not(hasItem("SUBSCRIPTION_MANAGE"))));

		long owners = mongo.count(Query.query(Criteria.where("tenantId").is(tenants().tenantId(org))
				.and("role").is("DONO")), MemberDocument.COLLECTION);
		assertEquals(0, owners, "only the purchase flow assigns DONO (ADR_121)");
	}

	@Test
	void memberGetsTheMemberPermissionsInAlphabeticalOrder() throws Exception {
		String org = organization();
		mvc.perform(get("/api/company/access/me").with(session(org, "user_member", "member")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("MEMBER"))
				.andExpect(jsonPath("$.permissions.length()").value(3))
				.andExpect(jsonPath("$.permissions[0]").value("COMPANY_VIEW"))
				.andExpect(jsonPath("$.permissions[1]").value("PRODUCTS_CREATE"))
				.andExpect(jsonPath("$.permissions[2]").value("PRODUCTS_EDIT"));
	}

	@Test
	void catalogShowsTheOwnerTheRolesAndTheMatrix() throws Exception {
		String org = organization();
		owner(org, "user_owner");
		mvc.perform(get("/api/company/access").with(session(org, "user_admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").value("user_owner"))
				.andExpect(jsonPath("$.roles.length()").value(3))
				.andExpect(jsonPath("$.roles[0].key").value("OWNER"))
				.andExpect(jsonPath("$.roles[0].label").value("Dono"))
				.andExpect(jsonPath("$.roles[2].label").value("Membro"))
				.andExpect(jsonPath("$.matrix[?(@.permission=='COMPANY_EDIT')].roles[0]").value("OWNER"))
				.andExpect(jsonPath("$.matrix[?(@.permission=='COMPANY_EDIT')].roles.length()").value(2))
				.andExpect(jsonPath("$.matrix[?(@.permission=='PRODUCTS_EDIT')].note")
						.value("MEMBER só nos produtos liberados"))
				.andExpect(jsonPath("$.matrix[?(@.permission=='COMPANY_EDIT')].note").doesNotExist());
	}

	@Test
	void catalogWithoutOwnerHasNullOwner() throws Exception {
		String org = organization();
		mvc.perform(get("/api/company/access").with(session(org, "user_admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").isEmpty());
	}

	@Test
	void ownerDemotedOutsideTheSystemIsMemberAndWarns(CapturedOutput output) throws Exception {
		String org = organization();
		owner(org, "user_owner");
		mvc.perform(get("/api/company/access/me").with(session(org, "user_owner", "member")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("MEMBER"));
		assertTrue(output.getOut().contains("event=access.owner.not_admin"));
		assertFalse(output.getOut().contains("userId=user_owner"), "the log must not carry the user id in clear text");
	}

	@Test
	void memberCannotReadTheCatalogAndKeepsTheSession() throws Exception {
		String org = organization();
		mvc.perform(get("/api/company/access").with(session(org, "user_member", "member")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("not_admin"))
				.andExpect(jsonPath("$.message").value(NOT_ALLOWED));
	}

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get("/api/company/access/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/company/access")).andExpect(status().isUnauthorized());
	}

	@Test
	void removedOrDeletedOwnerIsNotOwner() throws Exception {
		String org = organization();
		String tenantId = tenants().tenantId(org);
		mongo.insert(new MemberDocument(UUID.randomUUID().toString(), tenantId, "user_removed", null, "DONO", null, null,
				1, 0, Instant.parse("2026-11-01T00:00:00Z"), null));
		mongo.insert(new MemberDocument(UUID.randomUUID().toString(), tenantId, "user_deleted", null, "DONO", null, null,
				1, 0, null, Instant.parse("2026-11-01T00:00:00Z")));

		mvc.perform(get("/api/company/access/me").with(session(org, "user_removed", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
		mvc.perform(get("/api/company/access").with(session(org, "user_deleted", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").isEmpty());
	}

	@Test
	void duplicatedOwnersPickTheSmallestUserIdAndWarn(CapturedOutput output) throws Exception {
		String org = organization();
		owner(org, "user_b");
		owner(org, "user_a");
		mvc.perform(get("/api/company/access").with(session(org, "user_b", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").value("user_a"));
		assertTrue(output.getOut().contains("event=access.owner.duplicated"));
	}

	/** Active member with role DONO, as the purchase flow creates it (ADR_121, spec 003). */
	private String owner(String org, String user) {
		String memberId = UUID.randomUUID().toString();
		mongo.insert(new MemberDocument(memberId, tenants().tenantId(org), user, "mem_" + user, "DONO", null, null, 1, 0,
				null, null));
		return memberId;
	}

	private Document memberDocument(String memberId) {
		return mongo.findOne(Query.query(Criteria.where("_id").is(memberId)), Document.class, MemberDocument.COLLECTION);
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
		return "org_access_" + UUID.randomUUID();
	}
}
