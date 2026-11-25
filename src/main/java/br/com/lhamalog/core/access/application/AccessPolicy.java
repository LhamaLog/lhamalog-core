package br.com.lhamalog.core.access.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;

import br.com.lhamalog.core.shared.ApiStatusException;

/**
 * Checks a role against the {@link Permission} matrix. A refusal is a business 403 with a code, so the Web keeps the
 * session (ADR_109/126, spec 008 FR-010).
 */
public final class AccessPolicy {

	public static final String NOT_ALLOWED = "Somente o Dono ou um Administrador pode fazer esta ação.";

	public static final String NOT_ALLOWED_CODE = "not_admin";

	private AccessPolicy() {
	}

	public static boolean allows(AccessRole role, Permission permission) {
		return permission.roles().contains(role);
	}

	/** @throws ApiStatusException 403 {@code not_admin} when the role lacks the permission */
	public static void require(AccessRole role, Permission permission) {
		if (!allows(role, permission)) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, NOT_ALLOWED, NOT_ALLOWED_CODE);
		}
	}

	/** Permissions of the role, in alphabetical order of their names. */
	public static List<Permission> permissionsOf(AccessRole role) {
		return Arrays.stream(Permission.values())
			.filter((permission) -> allows(role, permission))
			.sorted(Comparator.comparing(Enum::name))
			.toList();
	}

}
