package br.com.lhamalog.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.lhamalog.core.member.adapter.out.mongo.DeletedUserDocument;
import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.support.TestSvix;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import br.com.lhamalog.core.webhook.adapter.out.mongo.ProcessedWebhookDocument;

/** End-to-end behaviour of POST /webhooks/clerk (spec LLT-164, US1 to US5). */
@CoreIntegrationTest
class ClerkWebhookTests {

	private static final String PATH = "/webhooks/clerk";

	private static final String ORG = "org_webhook";

	private static final String TENANT_ID = "tenant-webhook-0001";

	private static final String USER = "user_webhook";

	private static final String OK = "{\"status\":\"ok\"}";

	private static final String INVALID = "{\"error\":\"invalid_webhook\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void setUp() {
		mongoTemplate.remove(new Query(), TenantDocument.class);
		mongoTemplate.remove(new Query(), MemberDocument.class);
		mongoTemplate.remove(new Query(), DeletedUserDocument.class);
		mongoTemplate.remove(new Query(), ProcessedWebhookDocument.class);
		mongoTemplate.insert(new TenantDocument(TENANT_ID, ORG, "Webhook tenant", "ACTIVE"));
	}

	// US1: authenticity

	@Test
	void validSignatureIsAccepted() throws Exception {
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk())
			.andExpect(content().json(OK, true));
	}

	@Test
	void invalidSignatureIsRejectedAndNothingIsStored() throws Exception {
		String body = membership("organizationMembership.created", ORG, USER, "org:admin", 1000);
		long now = TestSvix.now();
		postSigned("msg_bad", now, TestSvix.sign(TestSvix.randomSecret(), "msg_bad", now, body), body)
			.andExpect(status().isBadRequest())
			.andExpect(content().json(INVALID, true));
		assertThat(memberCount()).isZero();
		assertThat(mongoTemplate.count(new Query(), ProcessedWebhookDocument.class)).isZero();
	}

	@Test
	void tamperedBodyIsRejected() throws Exception {
		String body = membership("organizationMembership.created", ORG, USER, "org:member", 1000);
		long now = TestSvix.now();
		String signature = TestSvix.sign("msg_tampered", now, body);
		postSigned("msg_tampered", now, signature, body.replace("org:member", "org:admin")).andExpect(status().isBadRequest());
		assertThat(memberCount()).isZero();
	}

	@Test
	void timestampOutsideToleranceIsRejected() throws Exception {
		String body = membership("organizationMembership.created", ORG, USER, "org:admin", 1000);
		long old = TestSvix.now() - 600;
		postSigned("msg_old", old, TestSvix.sign("msg_old", old, body), body).andExpect(status().isBadRequest());
		long future = TestSvix.now() + 600;
		postSigned("msg_future", future, TestSvix.sign("msg_future", future, body), body)
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingSvixHeadersAreRejected() throws Exception {
		mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json(INVALID, true));
	}

	@Test
	void signedButMalformedPayloadIsRejected() throws Exception {
		send("not json").andExpect(status().isBadRequest());
		send("{\"type\":\"organizationMembership.created\",\"data\":{\"updated_at\":1}}")
			.andExpect(status().isBadRequest());
	}

	// US2: members follow memberships

	@Test
	void membershipCreatedMapsRoles() throws Exception {
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk());
		send(membership("organizationMembership.created", ORG, "user_member", "org:member", 1000))
			.andExpect(status().isOk());

		MemberDocument admin = member(USER);
		assertThat(admin.role()).isEqualTo("ADMIN");
		assertThat(admin.tenantId()).isEqualTo(TENANT_ID);
		assertThat(admin.name()).isEqualTo("Ana Silva");
		assertThat(admin.email()).isEqualTo("ana@example.test");
		assertThat(member("user_member").role()).isEqualTo("MEMBRO");
	}

	@Test
	void ownerIsNotDemotedBySync() throws Exception {
		insertMember(USER, "DONO", 1000);

		send(membership("organizationMembership.updated", ORG, USER, "org:member", 2000)).andExpect(status().isOk());
		send(membership("organizationMembership.updated", ORG, USER, "org:admin", 3000)).andExpect(status().isOk());

		assertThat(member(USER).role()).isEqualTo("DONO");
		assertThat(member(USER).membershipVersion()).isEqualTo(3000);
	}

	@Test
	void membershipDeletedMarksMemberRemoved() throws Exception {
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk());

		send(membershipDeleted(ORG, USER, System.currentTimeMillis())).andExpect(status().isOk());

		assertThat(member(USER).removedAt()).isNotNull();
	}

	@Test
	void organizationWithoutTenantIsIgnored() throws Exception {
		String svixId = "msg_" + UUID.randomUUID();
		sendAs(svixId, membership("organizationMembership.created", "org_without_tenant", USER, "org:admin", 1000))
			.andExpect(status().isOk());

		assertThat(memberCount()).isZero();
		assertThat(processed(svixId).tenantId()).isNull();
	}

	@Test
	void processedMembershipRecordsTenant() throws Exception {
		String svixId = "msg_" + UUID.randomUUID();
		sendAs(svixId, membership("organizationMembership.created", ORG, USER, "org:admin", 1000))
			.andExpect(status().isOk());

		assertThat(processed(svixId).tenantId()).isEqualTo(TENANT_ID);
		assertThat(processed(svixId).type()).isEqualTo("organizationMembership.created");
	}

	// US3: duplicates and out-of-order delivery

	@Test
	void sameSvixIdIsProcessedOnce() throws Exception {
		String svixId = "msg_" + UUID.randomUUID();
		String body = membership("organizationMembership.created", ORG, USER, "org:admin", 1000);
		sendAs(svixId, body).andExpect(status().isOk());
		// Change the stored state: a reprocessing of the same message would not touch it (same version), so use the
		// role to prove the second delivery is short-circuited by the svix-id.
		mongoTemplate.updateFirst(Query.query(Criteria.where("clerkUserId").is(USER)),
				new Update().set("membershipVersion", 1L).set("role", "MEMBRO"), MemberDocument.class);

		sendAs(svixId, body).andExpect(status().isOk()).andExpect(content().json(OK, true));

		assertThat(member(USER).role()).isEqualTo("MEMBRO");
		assertThat(memberCount()).isEqualTo(1);
	}

	@Test
	void olderMembershipEventIsIgnored() throws Exception {
		send(membership("organizationMembership.updated", ORG, USER, "org:member", 2000)).andExpect(status().isOk());
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk());

		assertThat(member(USER).role()).isEqualTo("MEMBRO");
		assertThat(member(USER).membershipVersion()).isEqualTo(2000);
	}

	@Test
	void deletionBeforeCreationDoesNotActivateMember() throws Exception {
		long deletedAt = System.currentTimeMillis();
		send(membershipDeleted(ORG, USER, deletedAt)).andExpect(status().isOk());
		send(membership("organizationMembership.created", ORG, USER, "org:admin", deletedAt - 1000))
			.andExpect(status().isOk());

		assertThat(member(USER).removedAt()).isNotNull();

		send(membership("organizationMembership.created", ORG, USER, "org:admin", deletedAt + 1000))
			.andExpect(status().isOk());
		assertThat(member(USER).removedAt()).isNull();
	}

	// US4: user data and deletion

	@Test
	void userUpdatedRefreshesAllMembersAndIgnoresOlderEvents() throws Exception {
		mongoTemplate.insert(new TenantDocument("tenant-webhook-0002", "org_second", "Second", "ACTIVE"));
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk());
		send(membership("organizationMembership.created", "org_second", USER, "org:member", 1000))
			.andExpect(status().isOk());

		send(user("user.updated", USER, "Ana", "Souza", "ana.souza@example.test", 5000)).andExpect(status().isOk());
		send(user("user.updated", USER, "Old", "Name", "old@example.test", 4000)).andExpect(status().isOk());

		List<MemberDocument> members = mongoTemplate.find(Query.query(Criteria.where("clerkUserId").is(USER)),
				MemberDocument.class);
		assertThat(members).hasSize(2).allSatisfy((member) -> {
			assertThat(member.name()).isEqualTo("Ana Souza");
			assertThat(member.email()).isEqualTo("ana.souza@example.test");
			assertThat(member.userVersion()).isEqualTo(5000);
		});
	}

	@Test
	void userDeletedSoftDeletesAndIsNeverRecreated() throws Exception {
		send(membership("organizationMembership.created", ORG, USER, "org:admin", 1000)).andExpect(status().isOk());

		send(userDeleted(USER)).andExpect(status().isOk());
		assertThat(member(USER).deletedAt()).isNotNull();

		send(membership("organizationMembership.updated", ORG, USER, "org:member", 9_999_999_999_999L))
			.andExpect(status().isOk());
		send(user("user.updated", USER, "Ghost", "User", "ghost@example.test", 9_999_999_999_999L))
			.andExpect(status().isOk());

		MemberDocument member = member(USER);
		assertThat(member.deletedAt()).isNotNull();
		assertThat(member.role()).isEqualTo("ADMIN");
		assertThat(member.name()).isEqualTo("Ana Silva");
	}

	@Test
	void userDeletedBeforeAnyMembershipBlocksCreation() throws Exception {
		send(userDeleted("user_gone")).andExpect(status().isOk());
		send(membership("organizationMembership.created", ORG, "user_gone", "org:admin", 1000))
			.andExpect(status().isOk());

		assertThat(memberCount()).isZero();
		assertThat(mongoTemplate.findById("user_gone", DeletedUserDocument.class)).isNotNull()
			.satisfies((deleted) -> assertThat(deleted.tenantId()).isNull());
	}

	@Test
	void userCreatedWithoutMembershipStoresNothing() throws Exception {
		send(user("user.created", "user_new", "New", "User", "new@example.test", 1000)).andExpect(status().isOk());

		assertThat(memberCount()).isZero();
	}

	// US5: unknown events and failures

	@Test
	void unknownEventIsAcknowledged() throws Exception {
		String svixId = "msg_" + UUID.randomUUID();
		sendAs(svixId, "{\"type\":\"session.created\",\"object\":\"event\",\"timestamp\":1,\"data\":{\"id\":\"sess_1\"}}")
			.andExpect(status().isOk());

		assertThat(processed(svixId)).isNotNull();
	}

	@Test
	void processingFailureAnswers5xxAndAllowsRetry() throws Exception {
		String svixId = "msg_" + UUID.randomUUID();
		String body = membership("organizationMembership.created", ORG, USER, "org:admin", 1000);
		rejectMemberWrites(true);
		try {
			sendAs(svixId, body).andExpect(status().isInternalServerError())
				.andExpect(content().json("{\"error\":\"processing_failed\"}", true));
			assertThat(mongoTemplate.findById(svixId, ProcessedWebhookDocument.class)).isNull();
		}
		finally {
			rejectMemberWrites(false);
		}

		sendAs(svixId, body).andExpect(status().isOk());
		assertThat(member(USER).role()).isEqualTo("ADMIN");
		assertThat(processed(svixId)).isNotNull();
	}

	// helpers

	private ResultActions send(String body) throws Exception {
		return sendAs("msg_" + UUID.randomUUID(), body);
	}

	private ResultActions sendAs(String svixId, String body) throws Exception {
		long now = TestSvix.now();
		return postSigned(svixId, now, TestSvix.sign(svixId, now, body), body);
	}

	private ResultActions postSigned(String svixId, long timestamp, String signature, String body) throws Exception {
		return mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
			.header("svix-id", svixId)
			.header("svix-timestamp", String.valueOf(timestamp))
			.header("svix-signature", signature)
			.content(body));
	}

	private static String membership(String type, String org, String user, String role, long updatedAt) {
		return """
				{"type":"%s","object":"event","timestamp":%d,"instance_id":"ins_test",
				 "data":{"id":"orgmem_%s","object":"organization_membership","role":"%s","updated_at":%d,
				  "organization":{"id":"%s","name":"Org"},
				  "public_user_data":{"user_id":"%s","first_name":"Ana","last_name":"Silva","identifier":"ana@example.test"}}}
				""".formatted(type, updatedAt, user, role, updatedAt, org, user);
	}

	private static String membershipDeleted(String org, String user, long timestamp) {
		return """
				{"type":"organizationMembership.deleted","object":"event","timestamp":%d,
				 "data":{"id":"orgmem_%s","role":"org:admin","updated_at":1,
				  "organization":{"id":"%s"},"public_user_data":{"user_id":"%s"}}}
				""".formatted(timestamp, user, org, user);
	}

	private static String user(String type, String user, String first, String last, String email, long updatedAt) {
		return """
				{"type":"%s","object":"event","timestamp":%d,
				 "data":{"id":"%s","object":"user","first_name":"%s","last_name":"%s","updated_at":%d,
				  "primary_email_address_id":"idn_primary",
				  "email_addresses":[{"id":"idn_other","email_address":"other@example.test"},
				                     {"id":"idn_primary","email_address":"%s"}]}}
				""".formatted(type, updatedAt, user, first, last, updatedAt, email);
	}

	private static String userDeleted(String user) {
		return """
				{"type":"user.deleted","object":"event","timestamp":%d,"data":{"id":"%s","object":"user","deleted":true}}
				""".formatted(System.currentTimeMillis(), user);
	}

	private MemberDocument member(String clerkUserId) {
		return mongoTemplate.findOne(Query.query(Criteria.where("clerkUserId").is(clerkUserId)), MemberDocument.class);
	}

	private long memberCount() {
		return mongoTemplate.count(new Query(), MemberDocument.class);
	}

	private ProcessedWebhookDocument processed(String svixId) {
		ProcessedWebhookDocument document = mongoTemplate.findById(svixId, ProcessedWebhookDocument.class);
		assertThat(document).as("processed webhook %s", svixId).isNotNull();
		return document;
	}

	private void insertMember(String clerkUserId, String role, long membershipVersion) {
		mongoTemplate.insert(new MemberDocument(UUID.randomUUID().toString(), TENANT_ID, clerkUserId, "orgmem_x", role,
				"Owner", "owner@example.test", membershipVersion, 0, null, null));
	}

	/** Real write failure: a MongoDB collection validator that no document satisfies. */
	private void rejectMemberWrites(boolean reject) {
		Document validator = reject
				? new Document("$jsonSchema", new Document("required", List.of("fieldThatNeverExists")))
				: new Document();
		mongoTemplate.executeCommand(new Document("collMod", MemberDocument.COLLECTION).append("validator", validator)
			.append("validationAction", "error"));
	}

}
