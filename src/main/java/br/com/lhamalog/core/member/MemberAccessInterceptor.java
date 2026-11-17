package br.com.lhamalog.core.member;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MemberAccessInterceptor implements HandlerInterceptor {
	static final String DEACTIVATED = "Seu acesso a esta empresa foi desativado.";
	static final String DEACTIVATED_CODE = "user_deactivated";

	private final CompanySessionResolver sessions;
	private final CompanyMemberService members;

	public MemberAccessInterceptor(CompanySessionResolver sessions, CompanyMemberService members) {
		this.sessions = sessions;
		this.members = members;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt) || !hasOrganization(jwt)) {
			return true;
		}
		CompanySession session = sessions.resolve(jwt);
		if (members.isDeactivated(session.tenantId(), session.userId())) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, DEACTIVATED, DEACTIVATED_CODE);
		}
		members.observe(session.tenantId(), session.userId(), session.administrator());
		return true;
	}

	private static boolean hasOrganization(Jwt jwt) {
		Map<String, Object> organization = jwt.getClaimAsMap("o");
		if (organization != null) {
			return organization.get("id") instanceof String id && !id.isBlank();
		}
		String organizationId = jwt.getClaimAsString("org_id");
		return organizationId != null && !organizationId.isBlank();
	}
}
