package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenAuditMongoRepository extends MongoRepository<TokenAuditDocument, String> {
	List<TokenAuditDocument> findByTenantIdAndProductIdOrderByAtDesc(String tenantId, String productId);
}
