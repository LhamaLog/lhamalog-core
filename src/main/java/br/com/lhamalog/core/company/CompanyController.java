package br.com.lhamalog.core.company;

import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
	private final CompanyService service;
	private final CompanySessionResolver sessions;
	public CompanyController(CompanyService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}
	public record SaveCompany(@NotNull @Valid Company.Data data, @NotNull @Valid Company.Context context,
			@NotNull @Valid Company.Address address, Long version) {}

	@GetMapping
	public Company get(@AuthenticationPrincipal Jwt jwt) {
		return service.get(sessions.resolve(jwt).tenantId());
	}

	@PutMapping
	public Company save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveCompany request) {
		return service.save(sessions.resolve(jwt).tenantId(), request);
	}
}
