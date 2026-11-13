package br.com.lhamalog.core.member.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB representation of a member (Membro). */
@Document(MemberDocument.COLLECTION)
public record MemberDocument(@Id String memberId, String tenantId, String clerkUserId, String clerkMembershipId,
		String role, String name, String email, long membershipVersion, long userVersion, Instant removedAt,
		Instant deletedAt) {

	public static final String COLLECTION = "member";

	static final String ID = "_id";

	static final String TENANT_ID = "tenantId";

	static final String CLERK_USER_ID = "clerkUserId";

	static final String MEMBERSHIP_VERSION = "membershipVersion";

	static final String USER_VERSION = "userVersion";

	static final String DELETED_AT = "deletedAt";

}
