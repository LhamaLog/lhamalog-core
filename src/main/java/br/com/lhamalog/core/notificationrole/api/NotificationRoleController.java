package br.com.lhamalog.core.notificationrole.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.access.application.AccessService;
import br.com.lhamalog.core.access.application.Permission;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.CreateRole;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.RenameRole;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.ReplaceAlertTypes;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.ReplaceMembers;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.ReplaceSeverities;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.RoleList;
import br.com.lhamalog.core.notificationrole.api.NotificationRoleViews.RoleView;
import br.com.lhamalog.core.notificationrole.application.NotificationRoleService;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;

/**
 * Notification roles of the company (feature 011, US3 and US4). Same as the {@code /papeis} proposed by ADR_139, in
 * English (constitution IV, clarify C2). Owner or administrator only; the permission is checked before anything else,
 * so a refused member does not even create the default roles.
 */
@RestController
@RequestMapping("/api/company/notification-roles")
public class NotificationRoleController {

	private final NotificationRoleService service;

	private final AccessService access;

	private final CompanySessionResolver sessions;

	public NotificationRoleController(NotificationRoleService service, AccessService access,
			CompanySessionResolver sessions) {
		this.service = service;
		this.access = access;
		this.sessions = sessions;
	}

	@GetMapping
	public RoleList list() {
		return RoleList.of(service.list(tenant()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoleView create(@RequestBody CreateRole request) {
		return RoleView.of(service.create(tenant(), request.name(), request.severities(), request.alertTypes()));
	}

	@GetMapping("/{roleId}")
	public RoleView get(@PathVariable String roleId) {
		return RoleView.of(service.get(tenant(), roleId));
	}

	@PatchMapping("/{roleId}")
	public RoleView rename(@PathVariable String roleId, @Valid @RequestBody RenameRole request) {
		return RoleView.of(service.rename(tenant(), roleId, request.name(), request.version()));
	}

	@DeleteMapping("/{roleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String roleId) {
		service.delete(tenant(), roleId);
	}

	@PutMapping("/{roleId}/severities")
	public RoleView replaceSeverities(@PathVariable String roleId, @Valid @RequestBody ReplaceSeverities request) {
		return RoleView.of(service.replaceSeverities(tenant(), roleId, request.severities(), request.version()));
	}

	@PutMapping("/{roleId}/alert-types")
	public RoleView replaceAlertTypes(@PathVariable String roleId, @Valid @RequestBody ReplaceAlertTypes request) {
		return RoleView.of(service.replaceAlertTypes(tenant(), roleId, request.alertTypes(), request.version()));
	}

	@PutMapping("/{roleId}/members")
	public RoleView replaceMembers(@PathVariable String roleId, @Valid @RequestBody ReplaceMembers request) {
		return RoleView.of(service.replaceMembers(tenant(), roleId, request.memberUserIds(), request.version()));
	}

	@PostMapping("/{roleId}/members/{userId}")
	public RoleView addMember(@PathVariable String roleId, @PathVariable String userId) {
		return RoleView.of(service.addMember(tenant(), roleId, userId));
	}

	@DeleteMapping("/{roleId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@PathVariable String roleId, @PathVariable String userId) {
		service.removeMember(tenant(), roleId, userId);
	}

	/** Tenant of the session, once the caller is known to hold {@link Permission#NOTIFICATION_ROLES_MANAGE}. */
	private String tenant() {
		var session = sessions.current();
		access.require(session, Permission.NOTIFICATION_ROLES_MANAGE);
		return session.tenantId();
	}

}
