package br.com.lhamalog.core.access.infrastructure;

import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import br.com.lhamalog.core.access.application.AccessRole;
import br.com.lhamalog.core.access.application.MemberRoleDirectory;
import br.com.lhamalog.core.member.CompanyMember;
import br.com.lhamalog.core.member.CompanyMemberRepository;
import br.com.lhamalog.core.member.adapter.out.mongo.MemberDocument;
import br.com.lhamalog.core.member.domain.MemberRole;

/**
 * Reads the LLT-163 {@code member} collection directly, without changing the member module, and the LLT-168
 * {@code CompanyMember} through its repository. Read only.
 */
@Component
public class MongoMemberRoleDirectory implements MemberRoleDirectory {

	static final String TENANT_ID = "tenantId";

	static final String CLERK_USER_ID = "clerkUserId";

	static final String ROLE = "role";

	static final String REMOVED_AT = "removedAt";

	static final String DELETED_AT = "deletedAt";

	private final MongoTemplate mongo;

	private final CompanyMemberRepository companyMembers;

	public MongoMemberRoleDirectory(MongoTemplate mongo, CompanyMemberRepository companyMembers) {
		this.mongo = mongo;
		this.companyMembers = companyMembers;
	}

	@Override
	public Optional<AccessRole> memberRole(String tenantId, String userId) {
		Query query = Query.query(active(tenantId).and(CLERK_USER_ID).is(userId));
		query.fields().include(ROLE);
		Document member = mongo.findOne(query, Document.class, MemberDocument.COLLECTION);
		return Optional.ofNullable(member).map((found) -> found.getString(ROLE)).flatMap(MongoMemberRoleDirectory::toAccessRole);
	}

	@Override
	public boolean seenAsAdministrator(String tenantId, String userId) {
		return companyMembers.findById(CompanyMember.idOf(tenantId, userId)).map(CompanyMember::administrator)
			.orElse(false);
	}

	@Override
	public List<String> activeOwners(String tenantId) {
		Query query = Query.query(active(tenantId).and(ROLE).is(MemberRole.DONO.name()))
			.with(Sort.by(Sort.Direction.ASC, CLERK_USER_ID));
		query.fields().include(CLERK_USER_ID);
		return mongo.find(query, Document.class, MemberDocument.COLLECTION).stream()
			.map((owner) -> owner.getString(CLERK_USER_ID))
			.toList();
	}

	/** Same membership the LLT-163 sync treats as current: not removed from the organization and not deleted. */
	private static Criteria active(String tenantId) {
		return Criteria.where(TENANT_ID).is(tenantId).and(REMOVED_AT).is(null).and(DELETED_AT).is(null);
	}

	private static Optional<AccessRole> toAccessRole(String storedRole) {
		if (storedRole == null) {
			return Optional.empty();
		}
		return switch (storedRole) {
			case "DONO" -> Optional.of(AccessRole.OWNER);
			case "ADMIN" -> Optional.of(AccessRole.ADMIN);
			case "MEMBRO" -> Optional.of(AccessRole.MEMBER);
			default -> Optional.empty();
		};
	}

}
