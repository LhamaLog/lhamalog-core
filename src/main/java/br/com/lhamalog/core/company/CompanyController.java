package br.com.lhamalog.core.company;

import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
	public Company get() {
		return service.get(sessions.current().tenantId());
	}

	@PutMapping
	public Company save(@Valid @RequestBody SaveCompany request) {
		return service.save(sessions.current().tenantId(), request);
	}
}
