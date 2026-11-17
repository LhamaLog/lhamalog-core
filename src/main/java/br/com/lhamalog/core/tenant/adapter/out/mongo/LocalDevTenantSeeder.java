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
 * Development only: makes sure a tenant exists for a Clerk organization, so the local environment works before the
 * purchase flow (which creates tenants, ADR_121) exists. Inserts an ACTIVE tenant when missing and never changes an
 * existing one (its status included); an existing tenant that is not ACTIVE is reported with a warning (spec 007).
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
		String tenantId = UUID.randomUUID().toString();
		Query query = Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
		Update update = new Update().setOnInsert("_id", tenantId)
			.setOnInsert("name", DEV_TENANT_NAME)
			.setOnInsert("status", TenantStatus.ACTIVE.name());
		TenantDocument previous = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().upsert(true).returnNew(false), TenantDocument.class);
		if (previous == null) {
			log.info("event=dev_tenant.seed.created | Development tenant created | clerkOrgId={} tenantId={}",
					clerkOrgId, tenantId);
			return true;
		}
		if (TenantStatus.ACTIVE.name().equals(previous.status())) {
			log.info("event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId={} status={}",
					clerkOrgId, previous.status());
		}
		else {
			log.warn("event=dev_tenant.seed.inactive | Development tenant exists but is not active; access stays denied"
					+ " | clerkOrgId={} status={}", clerkOrgId, previous.status());
		}
		return false;
	}

}
