package br.com.lhamalog.core.member;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyMemberRepository extends MongoRepository<CompanyMember, String> {
	List<CompanyMember> findByTenantIdOrderByUserIdAsc(String tenantId);

	long countByTenantIdAndAdministratorTrueAndStatus(String tenantId, String status);
}
