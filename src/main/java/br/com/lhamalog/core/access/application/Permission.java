package br.com.lhamalog.core.access.application;

import static br.com.lhamalog.core.access.application.AccessRole.ADMIN;
import static br.com.lhamalog.core.access.application.AccessRole.MEMBER;
import static br.com.lhamalog.core.access.application.AccessRole.OWNER;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Fixed permission matrix (feature 011, data-model.md). ADR_123 rules out custom access roles, so the matrix lives in
 * code. Permissions applied by the Web through Clerk, or not built in the Core yet, are listed so the Web can draw its
 * screens; the Core does not enforce them in this delivery.
 */
public enum Permission {

	COMPANY_VIEW(null, OWNER, ADMIN, MEMBER),

	COMPANY_EDIT(null, OWNER, ADMIN),

	MEMBERS_VIEW(null, OWNER, ADMIN),

	MEMBERS_INVITE_MEMBER(Notes.WEB, OWNER, ADMIN),

	MEMBERS_INVITE_ADMIN(Notes.WEB, OWNER),

	MEMBERS_PROMOTE_ADMIN(Notes.WEB, OWNER, ADMIN),

	MEMBERS_DEACTIVATE(null, OWNER, ADMIN),

	PRODUCT_ACCESS_MANAGE(null, OWNER, ADMIN),

	PRODUCTS_VIEW_ALL(null, OWNER, ADMIN),

	/** Members keep creating products and get access to what they create (clarify C7). */
	PRODUCTS_CREATE(null, OWNER, ADMIN, MEMBER),

	PRODUCTS_EDIT("MEMBER só nos produtos liberados", OWNER, ADMIN, MEMBER),

	PRODUCTS_ARCHIVE(null, OWNER, ADMIN),

	INGESTION_TOKENS_MANAGE(null, OWNER, ADMIN),

	NOTIFICATION_ROLES_MANAGE(null, OWNER, ADMIN),

	SUBSCRIPTION_MANAGE(Notes.NOT_IN_CORE, OWNER),

	PENDING_ACCESS_APPROVE(Notes.NOT_IN_CORE, OWNER);

	private final String note;

	private final Set<AccessRole> roles;

	Permission(String note, AccessRole first, AccessRole... others) {
		this.note = note;
		this.roles = Collections.unmodifiableSet(EnumSet.of(first, others));
	}

	/** Roles that hold this permission. */
	public Set<AccessRole> roles() {
		return roles;
	}

	/** Optional remark shown with the matrix (Portuguese), or null. */
	public String note() {
		return note;
	}

	private static final class Notes {

		static final String WEB = "Aplicada no Web, pela Clerk.";

		static final String NOT_IN_CORE = "Ainda não existe no Core.";

	}

}
