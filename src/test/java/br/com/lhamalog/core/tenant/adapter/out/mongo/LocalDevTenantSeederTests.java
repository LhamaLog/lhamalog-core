package br.com.lhamalog.core.tenant.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import br.com.lhamalog.core.support.CoreIntegrationTest;

@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LocalDevTenantSeederTests {

	@Autowired
	private MongoTemplate mongoTemplate;

	private Query byOrganization(String clerkOrgId) {
		return Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId));
	}

	private static String organization() {
		return "org_dev" + UUID.randomUUID().toString().replace("-", "");
	}

	@Test
	void createsActiveTenantOnceForTheOrganization(CapturedOutput output) {
		String org = organization();
		LocalDevTenantSeeder seeder = new LocalDevTenantSeeder(mongoTemplate);

		assertThat(seeder.seed(org)).isTrue();
		assertThat(seeder.seed(org)).isFalse();

		assertThat(mongoTemplate.count(byOrganization(org), TenantDocument.class)).isEqualTo(1);
		TenantDocument tenant = mongoTemplate.findOne(byOrganization(org), TenantDocument.class);
		assertThat(tenant.status()).isEqualTo("ACTIVE");
		assertThat(tenant.name()).isEqualTo(LocalDevTenantSeeder.DEV_TENANT_NAME);
		assertThat(UUID.fromString(tenant.tenantId())).isNotNull();
		assertThat(output.getOut()).contains("event=dev_tenant.seed.created | Development tenant created | clerkOrgId="
				+ org + " tenantId=" + tenant.tenantId());
		assertThat(output.getOut())
			.contains("event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId=" + org
					+ " status=ACTIVE");
	}

	@Test
	void neverChangesAnExistingTenantAndWarnsWhenItIsNotActive(CapturedOutput output) {
		String org = organization();
		mongoTemplate.insert(new TenantDocument("tenant-suspended-" + org, org, "Suspensa", "SUSPENDED"));

		assertThat(new LocalDevTenantSeeder(mongoTemplate).seed(org)).isFalse();

		TenantDocument tenant = mongoTemplate.findOne(byOrganization(org), TenantDocument.class);
		assertThat(tenant.tenantId()).isEqualTo("tenant-suspended-" + org);
		assertThat(tenant.status()).isEqualTo("SUSPENDED");
		assertThat(output.getOut()).containsPattern("WARN .*event=dev_tenant\\.seed\\.inactive \\| "
				+ "Development tenant exists but is not active; access stays denied \\| clerkOrgId=" + org
				+ " status=SUSPENDED");
	}

}
