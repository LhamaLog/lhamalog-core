package br.com.lhamalog.core.notificationrole.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.access.application.AccessService;
import br.com.lhamalog.core.access.application.Permission;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.PersonRoles;
import br.com.lhamalog.core.notificationrole.application.NotificationRoleService;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

/** Notification roles of one person (feature 011, US4, FR-019). Owner or administrator only. */
@RestController
public class MemberNotificationRoleController {

	private final NotificationRoleService service;

	private final AccessService access;

	private final CompanySessionResolver sessions;

	public MemberNotificationRoleController(NotificationRoleService service, AccessService access,
			CompanySessionResolver sessions) {
		this.service = service;
		this.access = access;
		this.sessions = sessions;
	}

	@GetMapping("/api/company/members/{userId}/notification-roles")
	public PersonRoles rolesOf(@PathVariable String userId) {
		CompanySession session = sessions.current();
		access.require(session, Permission.NOTIFICATION_ROLES_MANAGE);
		return PersonRoles.of(userId, service.rolesOf(session.tenantId(), userId));
	}

}
