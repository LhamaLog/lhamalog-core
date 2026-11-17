package br.com.lhamalog.core.member;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.member.CompanyMemberView.ListResponse;
import br.com.lhamalog.core.shared.ApiStatusException;

@Service
public class CompanyMemberService {
	static final String NOT_LAST_ADMIN = "Não é possível desativar o último administrador.";
	static final String USER_REQUIRED = "Informe o usuário.";
	static final String LAST_ADMIN = "last_admin";

	private final CompanyMemberRepository members;
	private final MongoTemplate mongo;
	private final MemberClock clock;

	public CompanyMemberService(CompanyMemberRepository members, MongoTemplate mongo, MemberClock clock) {
		this.members = members;
		this.mongo = mongo;
		this.clock = clock;
	}

	public ListResponse list(String tenantId) {
		List<CompanyMemberView> views = members.findByTenantIdOrderByUserIdAsc(tenantId).stream()
				.map(CompanyMemberView::from).toList();
		return new ListResponse(views);
	}

	public boolean isDeactivated(String tenantId, String userId) {
		return members.findById(CompanyMember.idOf(tenantId, userId))
				.map(member -> CompanyMember.DEACTIVATED.equals(member.status())).orElse(false);
	}

	public void observe(String tenantId, String userId, boolean administrator) {
		CompanyMember current = members.findById(CompanyMember.idOf(tenantId, userId)).orElse(null);
		if (current != null && CompanyMember.DEACTIVATED.equals(current.status())) {
			return;
		}
		Instant now = clock.instant();
		if (!MemberObservation.shouldWrite(current, administrator, now, clock.interval())) {
			return;
		}
		boolean wasActiveAdmin = current != null && current.activeAdministrator();
		if (wasActiveAdmin != administrator) {
			ensureCount(tenantId);
		}
		CompanyMember next = new CompanyMember(CompanyMember.idOf(tenantId, userId), tenantId, userId, administrator,
				CompanyMember.ACTIVE, now, current == null ? null : current.version());
		try {
			members.save(next);
		}
		catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
			return;
		}
		if (wasActiveAdmin == administrator) {
			return;
		}
		if (administrator) {
			increment(tenantId);
		}
		else {
			decrementIfPositive(tenantId);
		}
	}

	public CompanyMemberView deactivate(String tenantId, String userId) {
		String id = requireUserId(userId);
		CompanyMember current = members.findById(CompanyMember.idOf(tenantId, id)).orElse(null);
		if (current == null) {
			try {
				return CompanyMemberView.from(members.save(new CompanyMember(CompanyMember.idOf(tenantId, id), tenantId, id,
						false, CompanyMember.DEACTIVATED, null, null)));
			}
			catch (DuplicateKeyException exception) {
				return deactivate(tenantId, id);
			}
		}
		if (CompanyMember.DEACTIVATED.equals(current.status())) {
			return CompanyMemberView.from(current);
		}
		if (current.activeAdministrator()) {
			ensureCount(tenantId);
			if (mongo.findAndModify(countAboveOne(tenantId), new Update().inc("activeAdminCount", -1),
					FindAndModifyOptions.options().returnNew(false), CompanyAdminCount.class) == null) {
				throw new ApiStatusException(HttpStatus.FORBIDDEN, NOT_LAST_ADMIN, LAST_ADMIN);
			}
			try {
				return CompanyMemberView.from(members.save(deactivated(current)));
			}
			catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
				increment(tenantId);
				throw exception;
			}
		}
		return CompanyMemberView.from(members.save(deactivated(current)));
	}

	public CompanyMemberView reactivate(String tenantId, String userId) {
		String id = requireUserId(userId);
		CompanyMember current = members.findById(CompanyMember.idOf(tenantId, id)).orElse(null);
		if (current == null || CompanyMember.ACTIVE.equals(current.status())) {
			return current == null ? new CompanyMemberView(id, CompanyMember.ACTIVE, null) : CompanyMemberView.from(current);
		}
		if (current.administrator()) {
			ensureCount(tenantId);
		}
		CompanyMember saved = members.save(new CompanyMember(current.id(), current.tenantId(), current.userId(),
				current.administrator(), CompanyMember.ACTIVE, current.lastAccessAt(), current.version()));
		if (saved.administrator()) {
			increment(tenantId);
		}
		return CompanyMemberView.from(saved);
	}

	private static CompanyMember deactivated(CompanyMember current) {
		return new CompanyMember(current.id(), current.tenantId(), current.userId(), current.administrator(),
				CompanyMember.DEACTIVATED, current.lastAccessAt(), current.version());
	}

	private static String requireUserId(String userId) {
		if (userId == null || userId.isBlank() || userId.length() > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, USER_REQUIRED);
		}
		return userId;
	}

	private void ensureCount(String tenantId) {
		long counted = members.countByTenantIdAndAdministratorTrueAndStatus(tenantId, CompanyMember.ACTIVE);
		mongo.upsert(Query.query(Criteria.where("_id").is(tenantId)),
				new Update().setOnInsert("activeAdminCount", counted), CompanyAdminCount.class);
	}

	private void increment(String tenantId) {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(tenantId)), new Update().inc("activeAdminCount", 1),
				CompanyAdminCount.class);
	}

	private void decrementIfPositive(String tenantId) {
		mongo.findAndModify(Query.query(Criteria.where("_id").is(tenantId).and("activeAdminCount").gt(0)),
				new Update().inc("activeAdminCount", -1), FindAndModifyOptions.options(), CompanyAdminCount.class);
	}

	private static Query countAboveOne(String tenantId) {
		return Query.query(Criteria.where("_id").is(tenantId).and("activeAdminCount").gt(1));
	}
}
