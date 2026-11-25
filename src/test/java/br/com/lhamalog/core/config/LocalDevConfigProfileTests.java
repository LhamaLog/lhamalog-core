package br.com.lhamalog.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.com.lhamalog.core.occurrence.infrastructure.LocalOccurrenceSampleSeeder;
import br.com.lhamalog.core.tenant.adapter.out.mongo.LocalDevTenantSeeder;

/** Development tenants (spec 008, US4) and sample occurrences (spec 012, US4) exist only in the local profile. */
class LocalDevConfigProfileTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
		.withUserConfiguration(LocalDevConfig.class)
		.withPropertyValues("lhamalog.dev-tenant.clerk-org-id=org_exemplo1",
				"lhamalog.occurrences.sample-data.enabled=true");

	@Test
	void localProfileRegistersTheSeederAndTheRunner() {
		contextRunner.withPropertyValues("spring.profiles.active=local").run((context) -> {
			assertThat(context).hasSingleBean(LocalDevTenantSeeder.class);
			assertThat(context).hasBean("localDevTenantRunner");
			assertThat(context.getBean("localDevTenantRunner")).isInstanceOf(ApplicationRunner.class);
			assertThat(context).hasSingleBean(LocalOccurrenceSampleSeeder.class);
			assertThat(context).hasBean("localOccurrenceSampleRunner");
		});
	}

	@Test
	void prodProfileRegistersNothingEvenWithTheVariableSet() {
		contextRunner.withPropertyValues("spring.profiles.active=prod").run((context) -> {
			assertThat(context).doesNotHaveBean(LocalDevTenantSeeder.class);
			assertThat(context).doesNotHaveBean("localDevTenantRunner");
			assertThat(context).doesNotHaveBean(LocalOccurrenceSampleSeeder.class);
			assertThat(context).doesNotHaveBean("localOccurrenceSampleRunner");
		});
	}

}
