package br.com.lhamalog.core.access.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.access.application.AccessService;
import br.com.lhamalog.core.access.application.Permission;
import br.com.lhamalog.core.access.application.ProductAccessService;
import br.com.lhamalog.core.access.application.ProductAccessService.MemberProducts;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Products a member can access (feature 011, US2). Owner or administrator only. */
@RestController
@RequestMapping("/api/company/members/{userId}/products")
public class MemberProductAccessController {

	public static final int MAX_PRODUCTS = 500;

	private final ProductAccessService productAccess;

	private final AccessService access;

	private final CompanySessionResolver sessions;

	public MemberProductAccessController(ProductAccessService productAccess, AccessService access,
			CompanySessionResolver sessions) {
		this.productAccess = productAccess;
		this.access = access;
		this.sessions = sessions;
	}

	public record ReplaceProducts(@NotNull @Size(max = MAX_PRODUCTS) List<@NotBlank String> productIds) {
	}

	@GetMapping
	public MemberProducts list(@PathVariable String userId) {
		return productAccess.list(manager().tenantId(), userId);
	}

	@PutMapping
	public MemberProducts replace(@PathVariable String userId, @Valid @RequestBody ReplaceProducts request) {
		return productAccess.replace(manager().tenantId(), userId, request.productIds());
	}

	private CompanySession manager() {
		CompanySession session = sessions.current();
		access.require(session, Permission.PRODUCT_ACCESS_MANAGE);
		return session;
	}

}
