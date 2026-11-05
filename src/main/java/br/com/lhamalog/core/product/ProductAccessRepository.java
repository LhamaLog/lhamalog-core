package br.com.lhamalog.core.product;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductAccessRepository extends MongoRepository<ProductAccess, String> {
	List<ProductAccess> findByTenantIdAndClerkUserId(String tenantId, String userId);
	boolean existsByTenantIdAndProductIdAndClerkUserId(String tenantId, String productId, String userId);
}
