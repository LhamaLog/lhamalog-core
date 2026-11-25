package br.com.lhamalog.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.com.lhamalog.core.occurrence.infrastructure.LocalOccurrenceSampleSeeder;
import br.com.lhamalog.core.tenant.adapter.out.mongo.LocalDevTenantSeeder;

/**
 * Local profile only. With {@code DEV_TENANT_CLERK_ORG_ID} set to one or more Clerk organization ids (comma
 * separated), creates an ACTIVE tenant for each one on startup; without it, does nothing. Invalid values are ignored
 * with a warning and never stop the startup. Never active in prod (spec 008).
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDevConfig {

	private static final Logger log = LoggerFactory.getLogger(LocalDevConfig.class);

	@Bean
	LocalDevTenantSeeder localDevTenantSeeder(MongoTemplate mongoTemplate) {
		return new LocalDevTenantSeeder(mongoTemplate);
	}

	@Bean
	ApplicationRunner localDevTenantRunner(LocalDevTenantSeeder seeder,
			@Value("${lhamalog.dev-tenant.clerk-org-id:}") String clerkOrgIds) {
		return (args) -> seedAll(seeder, clerkOrgIds);
	}

	/** Spec 012: sample occurrences for every product, only when {@code OCCURRENCE_SAMPLE_DATA=true}. */
	@Bean
	LocalOccurrenceSampleSeeder localOccurrenceSampleSeeder(MongoTemplate mongoTemplate) {
		return new LocalOccurrenceSampleSeeder(mongoTemplate);
	}

	@Bean
	ApplicationRunner localOccurrenceSampleRunner(LocalOccurrenceSampleSeeder seeder,
			@Value("${lhamalog.occurrences.sample-data.enabled:false}") boolean enabled) {
		return (args) -> {
			if (enabled) seeder.seed();
		};
	}

	static void seedAll(LocalDevTenantSeeder seeder, String clerkOrgIds) {
		DevTenantOrganizations organizations = DevTenantOrganizations.parse(clerkOrgIds);
		for (String discarded : organizations.discarded()) {
			log.warn("event=dev_tenant.seed.invalid_org_id | Ignored value that is not a Clerk organization id | value={}",
					discarded);
		}
		for (String clerkOrgId : organizations.valid()) {
			seeder.seed(clerkOrgId);
		}
	}

}
