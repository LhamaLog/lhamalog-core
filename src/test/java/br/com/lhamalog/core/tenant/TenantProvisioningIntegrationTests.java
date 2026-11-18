package br.com.lhamalog.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.com.lhamalog.core.support.CoreIntegrationTest;
import br.com.lhamalog.core.tenant.adapter.out.mongo.MongoTenantRepository;
import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning.ProvisionedTenant;

/** Spec 010: tenant creation by a purchase (research R5). */
@CoreIntegrationTest
class TenantProvisioningIntegrationTests {

	@Autowired
	private MongoTenantRepository repository;

	@Autowired
	private MongoTemplate mongo;

	@Test
	void createsAnActiveTenantMarkedWithTheOrder() {
		String org = newOrg();
		ProvisionedTenant tenant = repository.provisionForOrder(org, "Empresa", "order-1");
		assertThat(tenant.created()).isTrue();
		assertThat(tenant.createdBy("order-1")).isTrue();
		TenantDocument stored = mongo.findById(tenant.tenantId(), TenantDocument.class);
		assertThat(stored.clerkOrgId()).isEqualTo(org);
		assertThat(stored.name()).isEqualTo("Empresa");
		assertThat(stored.status()).isEqualTo("ACTIVE");
		assertThat(stored.originOrderId()).isEqualTo("order-1");
		assertThat(repository.findByClerkOrgId(org)).hasValueSatisfying((t) -> assertThat(t.isActive()).isTrue());
	}

	@Test
	void secondCallKeepsTheExistingTenant() {
		String org = newOrg();
		ProvisionedTenant first = repository.provisionForOrder(org, "Empresa", "order-1");
		ProvisionedTenant second = repository.provisionForOrder(org, "Outro nome", "order-2");
		assertThat(second.tenantId()).isEqualTo(first.tenantId());
		assertThat(second.created()).isFalse();
		assertThat(second.createdBy("order-2")).isFalse();
		assertThat(second.createdBy("order-1")).isTrue();
		assertThat(mongo.findById(first.tenantId(), TenantDocument.class).name()).isEqualTo("Empresa");
	}

	@Test
	void tenantCreatedElsewhereHasNoOriginOrder() {
		String org = newOrg();
		mongo.insert(new TenantDocument("tenant-" + org, org, "Seed", "SUSPENDED"));
		ProvisionedTenant tenant = repository.provisionForOrder(org, "Empresa", "order-1");
		assertThat(tenant.tenantId()).isEqualTo("tenant-" + org);
		assertThat(tenant.originOrderId()).isNull();
		assertThat(mongo.findById("tenant-" + org, TenantDocument.class).status()).isEqualTo("SUSPENDED");
	}

	private static String newOrg() {
		return "org_prov_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

}
