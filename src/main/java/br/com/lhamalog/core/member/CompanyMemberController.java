package br.com.lhamalog.core.member;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.access.application.AccessService;
import br.com.lhamalog.core.member.CompanyMemberView.ListResponse;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

@RestController
@RequestMapping("/api/company/members")
public class CompanyMemberController {
	static final String NOT_ADMIN = "Somente o administrador da empresa pode gerenciar os usuários.";
	static final String NOT_ADMIN_CODE = "not_admin";
	// LLT-120 (feature 011, FR-020): the owner can never be deactivated through the system.
	static final String OWNER_PROTECTED = "Não é possível desativar o Dono da empresa.";
	static final String OWNER_PROTECTED_CODE = "owner_protected";

	private final CompanyMemberService service;
	private final CompanySessionResolver sessions;
	private final AccessService access;

	public CompanyMemberController(CompanyMemberService service, CompanySessionResolver sessions,
			AccessService access) {
		this.service = service;
		this.sessions = sessions;
		this.access = access;
	}

	@GetMapping
	public ListResponse list() {
		return service.list(requireAdministrator().tenantId());
	}

	@PostMapping("/{userId}/deactivation")
	public CompanyMemberView deactivate(@PathVariable String userId) {
		String tenantId = requireAdministrator().tenantId();
		if (access.isOwner(tenantId, userId)) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, OWNER_PROTECTED, OWNER_PROTECTED_CODE);
		}
		return service.deactivate(tenantId, userId);
	}

	@DeleteMapping("/{userId}/deactivation")
	public CompanyMemberView reactivate(@PathVariable String userId) {
		return service.reactivate(requireAdministrator().tenantId(), userId);
	}

	private CompanySession requireAdministrator() {
		CompanySession session = sessions.current();
		if (!session.administrator()) {
			throw new ApiStatusException(HttpStatus.FORBIDDEN, NOT_ADMIN, NOT_ADMIN_CODE);
		}
		return session;
	}
}
