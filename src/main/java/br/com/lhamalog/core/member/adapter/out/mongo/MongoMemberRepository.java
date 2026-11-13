package br.com.lhamalog.core.member.adapter.out.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.member.application.port.out.ConcurrentMemberUpdateException;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.member.domain.Member;
import br.com.lhamalog.core.member.domain.MemberRole;

/** MongoDB adapter of {@link MemberRepository}. Updates use optimistic conditions on the versions read. */
@Repository
public class MongoMemberRepository implements MemberRepository, InitializingBean {

	static final String TENANT_USER_INDEX = "member_tenant_user";

	static final String CLERK_USER_INDEX = "member_clerk_user";

	private final MongoTemplate mongoTemplate;

	public MongoMemberRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void afterPropertiesSet() {
		var indexes = mongoTemplate.indexOps(MemberDocument.class);
		indexes.createIndex(new Index().on(MemberDocument.TENANT_ID, Sort.Direction.ASC)
			.on(MemberDocument.CLERK_USER_ID, Sort.Direction.ASC)
			.unique()
			.named(TENANT_USER_INDEX));
		indexes.createIndex(new Index().on(MemberDocument.CLERK_USER_ID, Sort.Direction.ASC).named(CLERK_USER_INDEX));
	}

	@Override
	public Optional<Member> findByTenantIdAndClerkUserId(String tenantId, String clerkUserId) {
		Query query = Query.query(
				Criteria.where(MemberDocument.TENANT_ID).is(tenantId).and(MemberDocument.CLERK_USER_ID).is(clerkUserId));
		return Optional.ofNullable(mongoTemplate.findOne(query, MemberDocument.class)).map(MongoMemberRepository::toDomain);
	}

	@Override
	public List<Member> findByClerkUserId(String clerkUserId) {
		Query query = Query.query(Criteria.where(MemberDocument.CLERK_USER_ID).is(clerkUserId));
		return mongoTemplate.find(query, MemberDocument.class).stream().map(MongoMemberRepository::toDomain).toList();
	}

	@Override
	public void insert(Member member) {
		mongoTemplate.insert(toDocument(member));
	}

	@Override
	public void update(Member previous, Member updated) {
		Query query = Query.query(Criteria.where(MemberDocument.ID)
			.is(previous.memberId())
			.and(MemberDocument.TENANT_ID)
			.is(previous.tenantId())
			.and(MemberDocument.MEMBERSHIP_VERSION)
			.is(previous.membershipVersion())
			.and(MemberDocument.USER_VERSION)
			.is(previous.userVersion())
			.and(MemberDocument.DELETED_AT)
			.is(null));
		Update update = new Update().set("clerkMembershipId", updated.clerkMembershipId())
			.set("role", updated.role().name())
			.set("name", updated.name())
			.set("email", updated.email())
			.set(MemberDocument.MEMBERSHIP_VERSION, updated.membershipVersion())
			.set(MemberDocument.USER_VERSION, updated.userVersion())
			.set("removedAt", updated.removedAt())
			.set(MemberDocument.DELETED_AT, updated.deletedAt());
		if (mongoTemplate.updateFirst(query, update, MemberDocument.class).getMatchedCount() == 0) {
			throw new ConcurrentMemberUpdateException(previous.memberId());
		}
	}

	private static MemberDocument toDocument(Member member) {
		return new MemberDocument(member.memberId(), member.tenantId(), member.clerkUserId(), member.clerkMembershipId(),
				member.role().name(), member.name(), member.email(), member.membershipVersion(), member.userVersion(),
				member.removedAt(), member.deletedAt());
	}

	private static Member toDomain(MemberDocument document) {
		return new Member(document.memberId(), document.tenantId(), document.clerkUserId(),
				document.clerkMembershipId(), MemberRole.valueOf(document.role()), document.name(), document.email(),
				document.membershipVersion(), document.userVersion(), document.removedAt(), document.deletedAt());
	}

}
