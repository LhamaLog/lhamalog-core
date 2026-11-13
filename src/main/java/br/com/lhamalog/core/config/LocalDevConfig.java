package br.com.lhamalog.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.com.lhamalog.core.tenant.adapter.out.mongo.LocalDevTenantSeeder;

/**
 * Local profile only. With {@code DEV_TENANT_CLERK_ORG_ID} set, creates an ACTIVE tenant for that Clerk organization
 * on startup; without it, does nothing. Never active in prod.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDevConfig {

	@Bean
	LocalDevTenantSeeder localDevTenantSeeder(MongoTemplate mongoTemplate) {
		return new LocalDevTenantSeeder(mongoTemplate);
	}

	@Bean
	ApplicationRunner localDevTenantRunner(LocalDevTenantSeeder seeder,
			@Value("${lhamalog.dev-tenant.clerk-org-id:}") String clerkOrgId) {
		return (args) -> {
			if (!clerkOrgId.isBlank()) {
				seeder.seed(clerkOrgId.trim());
			}
		};
	}

}
