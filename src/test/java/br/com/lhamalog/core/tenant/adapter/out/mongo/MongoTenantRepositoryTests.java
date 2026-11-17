package br.com.lhamalog.core.tenant.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.tenant.domain.Tenant;
import br.com.lhamalog.core.tenant.domain.TenantStatus;

@CoreIntegrationTest
class MongoTenantRepositoryTests {

	@Autowired
	private MongoTenantRepository repository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void setUp() {
		mongoTemplate.remove(new Query(), TenantDocument.class);
	}

	@Test
	void findsTenantByClerkOrgId() {
		mongoTemplate.insert(new TenantDocument("tenant-1", "org_1", "Tenant One", "ACTIVE"));

		assertThat(repository.findByClerkOrgId("org_1"))
			.contains(new Tenant("tenant-1", "org_1", "Tenant One", TenantStatus.ACTIVE));
	}

	@Test
	void returnsEmptyWhenOrganizationHasNoTenant() {
		assertThat(repository.findByClerkOrgId("org_missing")).isEmpty();
	}

	@Test
	void unknownStatusNeverGrantsAccess() {
		mongoTemplate.insert(new TenantDocument("tenant-2", "org_2", "Tenant Two", "SOMETHING_NEW"));

		assertThat(repository.findByClerkOrgId("org_2")).hasValueSatisfying((tenant) -> {
			assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
			assertThat(tenant.isActive()).isFalse();
		});
	}

	@Test
	void clerkOrgIdIsUnique() {
		mongoTemplate.insert(new TenantDocument("tenant-3", "org_3", "First", "ACTIVE"));

		assertThatThrownBy(() -> mongoTemplate.insert(new TenantDocument("tenant-4", "org_3", "Second", "ACTIVE")))
			.isInstanceOf(DuplicateKeyException.class);
	}

}
