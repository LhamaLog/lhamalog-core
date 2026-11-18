package br.com.lhamalog.core.member;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.lhamalog.core.identity.adapter.in.security.ClerkAuthentication;
import br.com.lhamalog.core.identity.domain.AccessDecision;
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
		// The security chain (LLT-163) already granted every /api/** request: active organization, known role and an
		// ACTIVE tenant. Without that context (public routes) there is nothing to check.
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof ClerkAuthentication clerk)
				|| !(clerk.decision() instanceof AccessDecision.Granted)) {
			return true;
		}
		CompanySession session = sessions.current();
		if (members.isDeactivated(session.tenantId(), session.userId())) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, DEACTIVATED, DEACTIVATED_CODE);
		}
		members.observe(session.tenantId(), session.userId(), session.administrator());
		return true;
	}
}
