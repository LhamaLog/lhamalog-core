package br.com.lhamalog.core.access.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.access.api.AccessViews.CatalogView;
import br.com.lhamalog.core.access.api.AccessViews.MeView;
import br.com.lhamalog.core.access.application.AccessService;
import br.com.lhamalog.core.access.application.Permission;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;

/** Access role of the caller and the permission matrix (feature 011, US1). Read only. */
@RestController
@RequestMapping("/api/company/access")
public class AccessController {

	private final AccessService access;

	private final CompanySessionResolver sessions;

	public AccessController(AccessService access, CompanySessionResolver sessions) {
		this.access = access;
		this.sessions = sessions;
	}

	@GetMapping("/me")
	public MeView me() {
		CompanySession session = sessions.current();
		return MeView.of(session.userId(), access.resolve(session));
	}

	@GetMapping
	public CatalogView catalog() {
		CompanySession session = sessions.current();
		access.require(session, Permission.MEMBERS_VIEW);
		return CatalogView.of(access.ownerOf(session.tenantId()).orElse(null));
	}

}
