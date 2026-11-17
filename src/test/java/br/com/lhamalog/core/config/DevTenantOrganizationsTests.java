package br.com.lhamalog.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevTenantOrganizationsTests {

	@Test
	void nullOrBlankMeansNoOrganization() {
		for (String raw : new String[] { null, "", "   ", ",", " , ," }) {
			DevTenantOrganizations organizations = DevTenantOrganizations.parse(raw);
			assertThat(organizations.valid()).isEmpty();
			assertThat(organizations.discarded()).isEmpty();
		}
	}

	@Test
	void acceptsASingleOrganization() {
		assertThat(DevTenantOrganizations.parse("org_exemploA1").valid()).containsExactly("org_exemploA1");
	}

	@Test
	void acceptsSeveralOrganizationsInOrder() {
		assertThat(DevTenantOrganizations.parse("org_exemploB2,org_exemploA1").valid())
			.containsExactly("org_exemploB2", "org_exemploA1");
	}

	@Test
	void stripsSpacesAndIgnoresEmptyItemsAndRepeats() {
		DevTenantOrganizations organizations = DevTenantOrganizations
			.parse(" org_exemploA1 , org_exemploA1,,org_exemploB2 ");
		assertThat(organizations.valid()).containsExactly("org_exemploA1", "org_exemploB2");
		assertThat(organizations.discarded()).isEmpty();
	}

	@Test
	void discardsValuesThatAreNotClerkOrganizationIds() {
		DevTenantOrganizations organizations = DevTenantOrganizations
			.parse("acme,org_,org_a-b,ORG_x,org_exemploA1,acme");
		assertThat(organizations.valid()).containsExactly("org_exemploA1");
		assertThat(organizations.discarded()).containsExactly("acme", "org_", "org_a-b", "ORG_x");
	}

}
