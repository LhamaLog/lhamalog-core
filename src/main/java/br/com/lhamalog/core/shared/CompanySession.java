package br.com.lhamalog.core.shared;

import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public record CompanySession(String tenantId, String userId, boolean administrator) {
	public static CompanySession from(Jwt jwt, Set<String> administratorRoles) {
		Map<String, Object> organization = jwt.getClaimAsMap("o");
		Object idValue = organization == null ? jwt.getClaimAsString("org_id") : organization.get("id");
		Object roleValue = organization == null ? jwt.getClaimAsString("org_role") : organization.get("rol");
		String id = idValue instanceof String value ? value : null;
		String role = roleValue instanceof String value ? value.replaceFirst("^org:", "") : "";
		if (id == null || id.isBlank()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selecione uma empresa para continuar.");
		}
		return new CompanySession(id, jwt.getSubject(), administratorRoles.contains(role));
	}
}
