package br.com.lhamalog.core.config;

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
import br.com.lhamalog.core.tenant.adapter.out.mongo.LocalDevTenantSeeder;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;

/** Several development organizations in one variable (spec 008, US3). */
@CoreIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LocalDevConfigTests {

	@Autowired
	private MongoTemplate mongoTemplate;

	private long tenantsOf(String clerkOrgId) {
		return mongoTemplate.count(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(clerkOrgId)),
				TenantDocument.class);
	}

	@Test
	void seedsEveryValidOrganizationOnceAndWarnsAboutInvalidValues(CapturedOutput output) {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String first = "org_a" + suffix;
		String second = "org_b" + suffix;
		String invalid = "invalido-" + suffix;
		LocalDevTenantSeeder seeder = new LocalDevTenantSeeder(mongoTemplate);
		String configured = " " + first + " , " + second + "," + first + ",," + invalid;

		LocalDevConfig.seedAll(seeder, configured);

		assertThat(tenantsOf(first)).isEqualTo(1);
		assertThat(tenantsOf(second)).isEqualTo(1);
		assertThat(tenantsOf(invalid)).isZero();
		assertThat(mongoTemplate.findOne(Query.query(Criteria.where(TenantDocument.CLERK_ORG_ID).is(second)),
				TenantDocument.class).status()).isEqualTo("ACTIVE");
		assertThat(output.getOut()).containsPattern("WARN .*event=dev_tenant\\.seed\\.invalid_org_id \\| "
				+ "Ignored value that is not a Clerk organization id \\| value=" + invalid);

		LocalDevConfig.seedAll(seeder, configured);

		assertThat(tenantsOf(first)).isEqualTo(1);
		assertThat(tenantsOf(second)).isEqualTo(1);
		assertThat(output.getOut()).contains("event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId="
				+ second + " status=ACTIVE");
	}

	@Test
	void emptyConfigurationSeedsNothing(CapturedOutput output) {
		long before = mongoTemplate.count(new Query(), TenantDocument.class);

		LocalDevConfig.seedAll(new LocalDevTenantSeeder(mongoTemplate), "  ");

		assertThat(mongoTemplate.count(new Query(), TenantDocument.class)).isEqualTo(before);
		assertThat(output.getOut()).doesNotContain("event=dev_tenant.seed.invalid_org_id");
	}

}
