package br.com.lhamalog.core.company;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyContextRevisionRepository extends MongoRepository<CompanyContextRevision, String> {
	List<CompanyContextRevision> findByTenantIdOrderByRevisionDesc(String tenantId);

	Optional<CompanyContextRevision> findByTenantIdAndRevision(String tenantId, long revision);

	void deleteByTenantId(String tenantId);
}
