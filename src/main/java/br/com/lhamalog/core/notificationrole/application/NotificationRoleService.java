package br.com.lhamalog.core.notificationrole.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.FieldValidationException;

/**
 * Catalog of notification roles of a company (feature 011, US3; ADR_136 to ADR_140, USID_052). Every operation first
 * makes sure the five default roles exist. Callers check the permission before calling.
 */
@Service
public class NotificationRoleService {

	static final String ROLE_NOT_FOUND = "Cargo não encontrado.";

	static final String ROLE_NOT_FOUND_CODE = "role_not_found";

	static final String NAME_IN_USE = "Já existe um cargo com este nome nesta empresa.";

	static final String NAME_IN_USE_CODE = "role_name_in_use";

	static final String IMMUTABLE = "Este cargo é padrão e não pode ser alterado.";

	static final String IMMUTABLE_CODE = "immutable_role";

	static final String NAME_REQUIRED = "Informe o nome do cargo, com até 60 caracteres.";

	static final String LIST_REQUIRED = "Escolha pelo menos um valor válido.";

	private static final Logger log = LoggerFactory.getLogger(NotificationRoleService.class);

	private final NotificationRoleRepository roles;

	private final Clock clock;

	public NotificationRoleService(NotificationRoleRepository roles, Clock clock) {
		this.roles = roles;
		this.clock = clock;
	}

	/** Active roles: the defaults in their fixed order, then the custom ones by name. */
	public List<NotificationRole> list(String tenantId) {
		roles.ensureDefaults(tenantId, clock.instant());
		return roles.findActive(tenantId).stream().sorted(DISPLAY_ORDER).toList();
	}

	public NotificationRole get(String tenantId, String roleId) {
		roles.ensureDefaults(tenantId, clock.instant());
		return find(tenantId, roleId);
	}

	public NotificationRole create(String tenantId, String name, List<String> severities, List<String> alertTypes) {
		Map<String, String> errors = new LinkedHashMap<>();
		String cleanName = validName(name, errors);
		List<Severity> cleanSeverities = validList("severities", severities, Severity::valueOf, errors);
		List<AlertType> cleanAlertTypes = validList("alertTypes", alertTypes, AlertType::valueOf, errors);
		if (!errors.isEmpty()) {
			throw new FieldValidationException(errors);
		}
		roles.ensureDefaults(tenantId, clock.instant());
		requireFreeName(tenantId, cleanName, null);
		NotificationRole created = withNameGuard(() -> roles.insert(NotificationRole.custom(UUID.randomUUID().toString(),
				tenantId, cleanName, cleanSeverities, cleanAlertTypes, clock.instant())));
		log.info("event=notification_role.created | Notification role created | tenantId={} roleId={}", tenantId,
				created.id());
		return created;
	}

	public NotificationRole rename(String tenantId, String roleId, String name, long version) {
		NotificationRole role = editable(tenantId, roleId);
		Map<String, String> errors = new LinkedHashMap<>();
		String cleanName = validName(name, errors);
		if (!errors.isEmpty()) {
			throw new FieldValidationException(errors);
		}
		requireVersion(role, version);
		requireFreeName(tenantId, cleanName, role.id());
		NotificationRole saved = withNameGuard(() -> roles.save(role.renamed(cleanName, clock.instant()).expecting(version)));
		logUpdated(saved, "name");
		return saved;
	}

	public NotificationRole replaceSeverities(String tenantId, String roleId, List<String> severities, long version) {
		NotificationRole role = editable(tenantId, roleId);
		List<Severity> clean = requireList("severities", severities, Severity::valueOf);
		requireVersion(role, version);
		NotificationRole saved = roles.save(role.withSeverities(clean, clock.instant()).expecting(version));
		logUpdated(saved, "severities");
		return saved;
	}

	public NotificationRole replaceAlertTypes(String tenantId, String roleId, List<String> alertTypes, long version) {
		NotificationRole role = editable(tenantId, roleId);
		List<AlertType> clean = requireList("alertTypes", alertTypes, AlertType::valueOf);
		requireVersion(role, version);
		NotificationRole saved = roles.save(role.withAlertTypes(clean, clock.instant()).expecting(version));
		logUpdated(saved, "alertTypes");
		return saved;
	}

	/** Logical and final deletion (ADR_139). The warning to owner and admins (ADR_138) belongs to Notification. */
	public void delete(String tenantId, String roleId) {
		NotificationRole role = editable(tenantId, roleId);
		roles.save(role.deletedAt(clock.instant()));
		log.info("event=notification_role.deleted | Notification role deleted for good | tenantId={} roleId={}",
				tenantId, role.id());
	}

	NotificationRole find(String tenantId, String roleId) {
		return roles.findActive(tenantId, roleId)
			.orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, ROLE_NOT_FOUND, ROLE_NOT_FOUND_CODE));
	}

	private NotificationRole editable(String tenantId, String roleId) {
		roles.ensureDefaults(tenantId, clock.instant());
		NotificationRole role = find(tenantId, roleId);
		if (role.immutable()) {
			throw new ApiStatusException(HttpStatus.CONFLICT, IMMUTABLE, IMMUTABLE_CODE);
		}
		return role;
	}

	static void requireVersion(NotificationRole role, long version) {
		if (role.version() == null || role.version() != version) {
			throw new OptimisticLockingFailureException("Notification role version conflict");
		}
	}

	private void requireFreeName(String tenantId, String name, String exceptRoleId) {
		String normalized = NotificationRole.normalize(name);
		boolean taken = roles.findActive(tenantId).stream()
			.anyMatch((other) -> normalized.equals(other.normalizedName()) && !other.id().equals(exceptRoleId));
		if (taken) {
			throw nameInUse();
		}
	}

	/** The unique index decides races between two requests with the same name. */
	private static NotificationRole withNameGuard(java.util.function.Supplier<NotificationRole> write) {
		try {
			return write.get();
		}
		catch (DuplicateKeyException sameName) {
			throw nameInUse();
		}
	}

	private static ApiStatusException nameInUse() {
		return new ApiStatusException(HttpStatus.CONFLICT, NAME_IN_USE, NAME_IN_USE_CODE);
	}

	private static String validName(String name, Map<String, String> errors) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty() || trimmed.length() > NotificationRole.NAME_MAX_LENGTH) {
			errors.put("name", NAME_REQUIRED);
			return null;
		}
		return trimmed;
	}

	private static <E extends Enum<E>> List<E> requireList(String field, List<String> values, Function<String, E> parse) {
		Map<String, String> errors = new LinkedHashMap<>();
		List<E> clean = validList(field, values, parse, errors);
		if (!errors.isEmpty()) {
			throw new FieldValidationException(errors);
		}
		return clean;
	}

	/** One or more values of the closed list (clarify C3), without repetition. */
	private static <E extends Enum<E>> List<E> validList(String field, List<String> values, Function<String, E> parse,
			Map<String, String> errors) {
		if (values == null || values.isEmpty()) {
			errors.put(field, LIST_REQUIRED);
			return List.of();
		}
		Set<E> clean = new LinkedHashSet<>();
		for (String value : values) {
			try {
				clean.add(parse.apply(value == null ? "" : value.trim()));
			}
			catch (IllegalArgumentException unknown) {
				errors.put(field, LIST_REQUIRED);
				return List.of();
			}
		}
		return new ArrayList<>(clean);
	}

	private static void logUpdated(NotificationRole role, String change) {
		log.info("event=notification_role.updated | Notification role changed | tenantId={} roleId={} change={}",
				role.tenantId(), role.id(), change);
	}

	private static final Comparator<NotificationRole> DISPLAY_ORDER = Comparator
		.comparing((NotificationRole role) -> role.defaultKey() == null ? Integer.MAX_VALUE : role.defaultKey().ordinal())
		.thenComparing(NotificationRole::normalizedName);

}
