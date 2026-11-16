package br.com.lhamalog.core.company;

import java.util.Optional;

import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Company fields of the tenant document. Never inserts, replaces or deletes the tenant document: the tenant module owns
 * it (name, status) and only purchase or the local seed create it (ADR_121). Writes are partial updates guarded by
 * {@code version} (absent before the first save).
 */
@Repository
public class CompanyRepository {

	private static final String[] COMPANY_FIELDS = { "data", "context", "address", "preferences", "settings", "logo",
			"updatedAt", "updatedByUserId", "version" };

	/** First version of the next save after {@link #clear}; absent while the company exists. */
	private static final String NEXT_VERSION = "nextCompanyVersion";

	private final MongoTemplate mongo;

	public CompanyRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public Optional<Company> findById(String tenantId) {
		return Optional.ofNullable(mongo.findById(tenantId, Company.class));
	}

	/**
	 * Writes the company fields if the stored version still equals {@code company.version()}, then increments it.
	 * @throws OptimisticLockingFailureException when the tenant is gone or someone saved in between
	 */
	public Company save(Company company) {
		Long expected = company.version();
		Criteria criteria = Criteria.where("_id").is(company.id());
		criteria = (expected == null) ? criteria.and("version").exists(false) : criteria.and("version").is(expected);
		long next = (expected == null) ? firstVersion(company.id()) : expected + 1;
		Update update = new Update().set("data", company.data())
			.set("context", company.context())
			.set("address", company.address())
			.set("preferences", company.preferences())
			.set("settings", company.settings())
			.set("logo", company.logo())
			.set("updatedAt", company.updatedAt())
			.set("updatedByUserId", company.updatedByUserId())
			.set("version", next)
			.unset(NEXT_VERSION);
		if (mongo.updateFirst(Query.query(criteria), update, Company.class).getMatchedCount() == 0) {
			throw new OptimisticLockingFailureException("Company version conflict");
		}
		return findById(company.id()).orElseThrow(() -> new OptimisticLockingFailureException("Tenant removed"));
	}

	/**
	 * Removes the company fields, keeping the tenant itself. The version numbering continues after a removal, so a
	 * screen loaded before it cannot overwrite the company registered again (its old version never comes back).
	 */
	public void clear(String tenantId) {
		Company current = findById(tenantId).orElse(null);
		if (current == null) {
			return;
		}
		Update update = new Update();
		for (String field : COMPANY_FIELDS) {
			update.unset(field);
		}
		if (current.version() != null) {
			update.set(NEXT_VERSION, current.version() + 1);
		}
		mongo.updateFirst(Query.query(Criteria.where("_id").is(tenantId)), update, Company.class);
	}

	private long firstVersion(String tenantId) {
		Query query = Query.query(Criteria.where("_id").is(tenantId));
		query.fields().include(NEXT_VERSION);
		Document stored = mongo.findOne(query, Document.class, CompanyService.TENANT_COLLECTION);
		Object next = (stored != null) ? stored.get(NEXT_VERSION) : null;
		return (next instanceof Number number) ? number.longValue() : 0L;
	}

}
