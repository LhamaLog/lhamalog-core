package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IngestionTokenMongoRepository extends MongoRepository<IngestionTokenDocument, String> {
	Optional<IngestionTokenDocument> findByIdAndTenantIdAndProductId(String id, String tenantId, String productId);

	List<IngestionTokenDocument> findByTenantIdAndProductIdOrderByCreatedAtDesc(String tenantId, String productId);
}
