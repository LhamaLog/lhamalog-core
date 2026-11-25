package br.com.lhamalog.core.access.application;

import static br.com.lhamalog.core.access.application.AccessRole.ADMIN;
import static br.com.lhamalog.core.access.application.AccessRole.MEMBER;
import static br.com.lhamalog.core.access.application.AccessRole.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.shared.ApiStatusException;

/** Feature 011 (LLT-120): the fixed permission matrix of data-model.md, row by row. No Spring. */
class AccessPolicyTest {

	/** Copy of the data-model.md table. A change in the matrix must change this test too. */
	private static final Map<Permission, Set<AccessRole>> MATRIX = Map.ofEntries(
			Map.entry(Permission.COMPANY_VIEW, EnumSet.of(OWNER, ADMIN, MEMBER)),
			Map.entry(Permission.COMPANY_EDIT, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.MEMBERS_VIEW, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.MEMBERS_INVITE_MEMBER, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.MEMBERS_INVITE_ADMIN, EnumSet.of(OWNER)),
			Map.entry(Permission.MEMBERS_PROMOTE_ADMIN, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.MEMBERS_DEACTIVATE, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.PRODUCT_ACCESS_MANAGE, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.PRODUCTS_VIEW_ALL, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.PRODUCTS_CREATE, EnumSet.of(OWNER, ADMIN, MEMBER)),
			Map.entry(Permission.PRODUCTS_EDIT, EnumSet.of(OWNER, ADMIN, MEMBER)),
			Map.entry(Permission.PRODUCTS_ARCHIVE, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.INGESTION_TOKENS_MANAGE, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.NOTIFICATION_ROLES_MANAGE, EnumSet.of(OWNER, ADMIN)),
			Map.entry(Permission.SUBSCRIPTION_MANAGE, EnumSet.of(OWNER)),
			Map.entry(Permission.PENDING_ACCESS_APPROVE, EnumSet.of(OWNER)));

	@Test
	void everyPermissionHasExactlyTheRolesOfTheDataModelTable() {
		assertEquals(MATRIX.keySet(), EnumSet.allOf(Permission.class), "a permission is missing from the table");
		for (Permission permission : Permission.values()) {
			assertEquals(MATRIX.get(permission), permission.roles(), permission.name());
			for (AccessRole role : AccessRole.values()) {
				assertEquals(MATRIX.get(permission).contains(role), AccessPolicy.allows(role, permission),
						permission + " for " + role);
			}
		}
	}

	@Test
	void permissionsOfARoleComeInAlphabeticalOrder() {
		List<Permission> member = AccessPolicy.permissionsOf(MEMBER);
		assertEquals(List.of(Permission.COMPANY_VIEW, Permission.PRODUCTS_CREATE, Permission.PRODUCTS_EDIT), member);
		List<Permission> owner = AccessPolicy.permissionsOf(OWNER);
		assertEquals(Permission.values().length, owner.size(), "the owner has every permission");
		List<String> names = owner.stream().map(Enum::name).toList();
		assertEquals(names.stream().sorted().toList(), names);
	}

	@Test
	void requireRefusesWithTheBusinessForbiddenCode() {
		ApiStatusException error = assertThrows(ApiStatusException.class,
				() -> AccessPolicy.require(MEMBER, Permission.MEMBERS_VIEW));
		assertEquals(403, error.getStatusCode().value());
		assertEquals(AccessPolicy.NOT_ALLOWED_CODE, error.code());
		assertEquals("not_admin", error.code());
		assertEquals("Somente o Dono ou um Administrador pode fazer esta ação.", error.getReason());
		AccessPolicy.require(ADMIN, Permission.MEMBERS_VIEW);
		AccessPolicy.require(OWNER, Permission.SUBSCRIPTION_MANAGE);
	}

	@Test
	void rolesHaveTheirPortugueseLabels() {
		assertEquals(List.of("Dono", "Administrador", "Membro"),
				Arrays.stream(AccessRole.values()).map(AccessRole::label).toList());
	}

	@Test
	void onlyTheMemberRestrictedEditHasAnExplanationAboutTheReleasedProducts() {
		assertEquals("MEMBER só nos produtos liberados", Permission.PRODUCTS_EDIT.note());
		assertNull(Permission.COMPANY_EDIT.note());
	}
}
