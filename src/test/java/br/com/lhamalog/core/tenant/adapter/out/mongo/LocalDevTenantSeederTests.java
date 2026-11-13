package br.com.lhamalog.core.tenant.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import br.com.lhamalog.core.support.CoreIntegrationTest;

@CoreIntegrationTest
class LocalDevTenantSeederTests {

	@Autowired
	private MongoTemplate mongoTemplate;

	private Query byOrganization(String clerkOrgId) {
		return Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
	}

	@Test
	void createsActiveTenantOnceForTheOrganization() {
		String org = "org_dev_" + UUID.randomUUID();
		LocalDevTenantSeeder seeder = new LocalDevTenantSeeder(mongoTemplate);

		assertThat(seeder.seed(org)).isTrue();
		assertThat(seeder.seed(org)).isFalse();

		assertThat(mongoTemplate.count(byOrganization(org), TenantDocument.class)).isEqualTo(1);
		TenantDocument tenant = mongoTemplate.findOne(byOrganization(org), TenantDocument.class);
		assertThat(tenant.status()).isEqualTo("ACTIVE");
		assertThat(tenant.name()).isEqualTo(LocalDevTenantSeeder.DEV_TENANT_NAME);
		assertThat(UUID.fromString(tenant.tenantId())).isNotNull();
	}

	@Test
	void neverChangesAnExistingTenant() {
		String org = "org_dev_" + UUID.randomUUID();
		mongoTemplate.insert(new TenantDocument("tenant-suspended-dev", org, "Suspensa", "SUSPENDED"));

		assertThat(new LocalDevTenantSeeder(mongoTemplate).seed(org)).isFalse();

		TenantDocument tenant = mongoTemplate.findOne(byOrganization(org), TenantDocument.class);
		assertThat(tenant.tenantId()).isEqualTo("tenant-suspended-dev");
		assertThat(tenant.status()).isEqualTo("SUSPENDED");
	}

}
