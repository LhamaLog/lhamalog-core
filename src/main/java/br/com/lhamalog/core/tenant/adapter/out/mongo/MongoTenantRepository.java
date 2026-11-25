package br.com.lhamalog.core.tenant.adapter.out.mongo;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning;
import br.com.lhamalog.core.tenant.application.port.out.TenantRepository;
import br.com.lhamalog.core.tenant.domain.Tenant;
import br.com.lhamalog.core.tenant.domain.TenantStatus;

/**
 * MongoDB adapter of {@link TenantRepository} and {@link TenantProvisioning}. Creates its indexes explicitly on startup
 * (idempotent); the unique index on {@code clerkOrgId} keeps one tenant per organization.
 */
@Repository
public class MongoTenantRepository implements TenantRepository, TenantProvisioning, InitializingBean {

	static final String CLERK_ORG_ID_INDEX = "tenant_clerk_org_id";

	private static final Logger log = LoggerFactory.getLogger(MongoTenantRepository.class);

	private final MongoTemplate mongoTemplate;

	public MongoTenantRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void afterPropertiesSet() {
		mongoTemplate.indexOps(TenantDocument.class)
			.createIndex(new Index().on(TenantDocument.CLERK_ORG_ID, Sort.Direction.ASC)
				.unique()
				.named(CLERK_ORG_ID_INDEX));
	}

	@Override
	public Optional<Tenant> findByClerkOrgId(String clerkOrgId) {
		Query query = Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
		return Optional.ofNullable(mongoTemplate.findOne(query, TenantDocument.class)).map(this::toDomain);
	}

	@Override
	public ProvisionedTenant provisionForOrder(String clerkOrgId, String name, String orderId) {
		try {
			return upsert(clerkOrgId, name, orderId);
		}
		catch (DuplicateKeyException ex) {
			// A concurrent upsert inserted the tenant first: read the winner.
			return upsert(clerkOrgId, name, orderId);
		}
	}

	private ProvisionedTenant upsert(String clerkOrgId, String name, String orderId) {
		String newTenantId = UUID.randomUUID().toString();
		Query query = Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
		Update update = new Update().setOnInsert("_id", newTenantId)
			.setOnInsert("name", name)
			.setOnInsert("status", TenantStatus.ACTIVE.name())
			.setOnInsert(TenantDocument.ORIGIN_ORDER_ID, orderId);
		TenantDocument stored = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().upsert(true).returnNew(true), TenantDocument.class);
		return new ProvisionedTenant(stored.tenantId(), stored.originOrderId(), newTenantId.equals(stored.tenantId()));
	}

	private Tenant toDomain(TenantDocument document) {
		return new Tenant(document.tenantId(), document.clerkOrgId(), document.name(), parseStatus(document));
	}

	private TenantStatus parseStatus(TenantDocument document) {
		if (document.status() != null) {
			for (TenantStatus status : TenantStatus.values()) {
				if (status.name().equals(document.status())) {
					return status;
				}
			}
		}
		// Unknown statuses never grant access.
		log.warn("Tenant {} has an unknown status; treating it as SUSPENDED", document.tenantId());
		return TenantStatus.SUSPENDED;
	}

}
