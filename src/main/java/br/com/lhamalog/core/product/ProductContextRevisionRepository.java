package br.com.lhamalog.core.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductContextRevisionRepository extends MongoRepository<ProductContextRevision, String> {
	List<ProductContextRevision> findByTenantIdAndProductIdOrderByRevisionDesc(String tenantId, String productId);

	Optional<ProductContextRevision> findByTenantIdAndProductIdAndRevision(String tenantId, String productId,
			long revision);

	Optional<ProductContextRevision> findFirstByProductIdOrderByRevisionDesc(String productId);
}
