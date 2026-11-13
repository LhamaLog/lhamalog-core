package br.com.lhamalog.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizationRoleTests {

	@ParameterizedTest
	@CsvSource({ "admin, ADMIN", "org:admin, ADMIN", "ADMIN, ADMIN", "member, MEMBER", "org:member, MEMBER",
			"' member ', MEMBER" })
	void normalizesClerkRoles(String raw, OrganizationRole expected) {
		assertThat(OrganizationRole.fromClerk(raw)).contains(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "billing", "org:", "owner", "org:owner" })
	void rejectsUnknownRoles(String raw) {
		assertThat(OrganizationRole.fromClerk(raw)).isEmpty();
	}

}
