package br.com.lhamalog.core.company;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads and updates the company data stored in the tenant document. Updates touch only data, context, address and
 * version, with optimistic concurrency on version (absent before the first save), so tenant name and status are
 * never overwritten.
 */
@Service
public class CompanyService {
	static final String TENANT_COLLECTION = "tenant";

	private final MongoTemplate mongo;

	public CompanyService(MongoTemplate mongo) { this.mongo = mongo; }

	public Company get(String tenantId) {
		Company stored = mongo.findById(tenantId, Company.class);
		if (stored == null) {
			// The security layer only grants access to existing tenants; this guards against a concurrent removal.
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada.");
		}
		return stored.withDefaults();
	}

	public Company save(String tenantId, CompanyController.SaveCompany request) {
		Criteria criteria = Criteria.where("_id").is(tenantId);
		criteria = (request.version() == null) ? criteria.and("version").exists(false)
				: criteria.and("version").is(request.version());
		long nextVersion = (request.version() == null) ? 0 : request.version() + 1;
		Update update = new Update().set("data", request.data())
			.set("context", request.context())
			.set("address", request.address())
			.set("version", nextVersion);
		if (mongo.updateFirst(Query.query(criteria), update, Company.class).getMatchedCount() == 0) {
			throw new OptimisticLockingFailureException("Company version conflict");
		}
		return get(tenantId);
	}
}
