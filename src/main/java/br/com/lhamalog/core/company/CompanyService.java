package br.com.lhamalog.core.company;

import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {
	private final CompanyRepository repository;

	public CompanyService(CompanyRepository repository) { this.repository = repository; }

	public Company get(String tenantId) {
		return repository.findById(tenantId).orElseGet(() -> Company.empty(tenantId));
	}

	public Company save(String tenantId, CompanyController.SaveCompany request) {
		Company current = get(tenantId);
		if (!Objects.equals(current.version(), request.version())) {
			throw new OptimisticLockingFailureException("Company version conflict");
		}
		return repository.save(new Company(tenantId, tenantId, request.data(),
				request.context(), request.address(), request.version()));
	}
}
