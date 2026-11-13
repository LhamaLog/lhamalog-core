package br.com.lhamalog.core.tenant.adapter.out.mongo;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import br.com.lhamalog.core.tenant.domain.TenantStatus;

/**
 * Development only: makes sure a tenant exists for one Clerk organization, so the local environment works before the
 * purchase flow (which creates tenants, ADR_121) exists. Inserts an ACTIVE tenant when missing and never changes an
 * existing one (its status included).
 */
public class LocalDevTenantSeeder {

	static final String DEV_TENANT_NAME = "Tenant de desenvolvimento";

	private static final Logger log = LoggerFactory.getLogger(LocalDevTenantSeeder.class);

	private final MongoTemplate mongoTemplate;

	public LocalDevTenantSeeder(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/** @return {@code true} when the tenant was created now, {@code false} when it already existed */
	public boolean seed(String clerkOrgId) {
		Query query = Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
		Update update = new Update().setOnInsert("_id", UUID.randomUUID().toString())
			.setOnInsert("name", DEV_TENANT_NAME)
			.setOnInsert("status", TenantStatus.ACTIVE.name());
		TenantDocument previous = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().upsert(true).returnNew(false), TenantDocument.class);
		boolean created = previous == null;
		if (created) {
			log.info("Development tenant created for organization {}", clerkOrgId);
		}
		else {
			log.info("Development tenant already exists for organization {} (status {})", clerkOrgId,
					previous.status());
		}
		return created;
	}

}
