package br.com.lhamalog.core.notificationrole.api;

import java.util.List;

import br.com.lhamalog.core.notificationrole.application.AlertType;
import br.com.lhamalog.core.notificationrole.application.DefaultRole;
import br.com.lhamalog.core.notificationrole.application.NotificationRole;
import br.com.lhamalog.core.notificationrole.application.Severity;
import jakarta.validation.constraints.NotNull;

/** Requests and responses of the notification role routes (feature 011, contracts/api.md section 3). */
public final class NotificationRoleViews {

	private NotificationRoleViews() {
	}

	/** Where the members of a role come from: owner and admin roles follow the access role (ADR_140). */
	public enum MembersSource {

		ACCESS_ROLE,

		EXPLICIT

	}

	public record RoleView(String id, String name, DefaultRole defaultKey, boolean isDefault, boolean immutable,
			List<Severity> severities, List<AlertType> alertTypes, List<String> memberUserIds,
			MembersSource membersSource, Long version) {

		public static RoleView of(NotificationRole role) {
			return new RoleView(role.id(), role.name(), role.defaultKey(), role.isDefault(), role.immutable(),
					role.severities(), role.alertTypes(), role.immutable() ? List.of() : role.memberUserIds(),
					role.immutable() ? MembersSource.ACCESS_ROLE : MembersSource.EXPLICIT, role.version());
		}

	}

	public record RoleList(List<RoleView> roles) {

		public static RoleList of(List<NotificationRole> roles) {
			return new RoleList(roles.stream().map(RoleView::of).toList());
		}

	}

	/** Lists are strings so an unknown value comes back as a field error, not as an unreadable body. */
	public record CreateRole(String name, List<String> severities, List<String> alertTypes) {
	}

	public record RenameRole(String name, @NotNull Long version) {
	}

	public record ReplaceSeverities(List<String> severities, @NotNull Long version) {
	}

	public record ReplaceAlertTypes(List<String> alertTypes, @NotNull Long version) {
	}

}
