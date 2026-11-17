package br.com.lhamalog.core.member;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.member.CompanyMemberView.ListResponse;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

@RestController
@RequestMapping("/api/company/members")
public class CompanyMemberController {
	static final String NOT_ADMIN = "Somente o administrador da empresa pode gerenciar os usuários.";
	static final String NOT_ADMIN_CODE = "not_admin";

	private final CompanyMemberService service;
	private final CompanySessionResolver sessions;

	public CompanyMemberController(CompanyMemberService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@GetMapping
	public ListResponse list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(requireAdministrator(jwt).tenantId());
	}

	@PostMapping("/{userId}/deactivation")
	public CompanyMemberView deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
		return service.deactivate(requireAdministrator(jwt).tenantId(), userId);
	}

	@DeleteMapping("/{userId}/deactivation")
	public CompanyMemberView reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
		return service.reactivate(requireAdministrator(jwt).tenantId(), userId);
	}

	private CompanySession requireAdministrator(Jwt jwt) {
		CompanySession session = sessions.resolve(jwt);
		if (!session.administrator()) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, NOT_ADMIN, NOT_ADMIN_CODE);
		}
		return session;
	}
}
