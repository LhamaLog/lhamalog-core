package br.com.lhamalog.core.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
	List<Product> findByTenantIdOrderByNameAscIdAsc(String tenantId);
	List<Product> findByTenantIdAndIdInOrderByNameAscIdAsc(String tenantId, Collection<String> ids);
	Optional<Product> findByIdAndTenantId(String id, String tenantId);
}
