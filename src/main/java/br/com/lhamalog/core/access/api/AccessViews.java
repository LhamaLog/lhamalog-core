package br.com.lhamalog.core.access.api;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.lhamalog.core.access.application.AccessPolicy;
import br.com.lhamalog.core.access.application.AccessRole;
import br.com.lhamalog.core.access.application.Permission;

/** Responses of the access routes (feature 011, contracts/api.md section 1). */
public final class AccessViews {

	private AccessViews() {
	}

	/** {@code GET /api/company/access/me}: role and permissions of the caller. */
	public record MeView(String userId, AccessRole role, List<Permission> permissions) {

		static MeView of(String userId, AccessRole role) {
			return new MeView(userId, role, AccessPolicy.permissionsOf(role));
		}

	}

	/** {@code GET /api/company/access}: owner, the three roles and the whole matrix. */
	public record CatalogView(String ownerUserId, List<RoleView> roles, List<MatrixRow> matrix) {

		static CatalogView of(String ownerUserId) {
			List<RoleView> roles = Arrays.stream(AccessRole.values()).map(RoleView::of).toList();
			List<MatrixRow> matrix = Arrays.stream(Permission.values()).map(MatrixRow::of).toList();
			return new CatalogView(ownerUserId, roles, matrix);
		}

	}

	public record RoleView(AccessRole key, String label) {

		static RoleView of(AccessRole role) {
			return new RoleView(role, role.label());
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MatrixRow(Permission permission, List<AccessRole> roles, String note) {

		static MatrixRow of(Permission permission) {
			List<AccessRole> roles = Arrays.stream(AccessRole.values()).filter(permission.roles()::contains).toList();
			return new MatrixRow(permission, roles, permission.note());
		}

	}

}
