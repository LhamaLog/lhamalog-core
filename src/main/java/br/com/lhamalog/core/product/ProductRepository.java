package br.com.lhamalog.core.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
	List<Product> findByTenantIdOrderByNameAscIdAsc(String tenantId);
	List<Product> findByTenantIdAndIdInOrderByNameAscIdAsc(String tenantId, Collection<String> ids);
	Optional<Product> findByIdAndTenantId(String id, String tenantId);

	/** How many of the ids are products of the company, archived ones included (feature 011, US2). */
	long countByTenantIdAndIdIn(String tenantId, Collection<String> ids);

	// Active products: archivedAt null or missing (documents created before the field existed).
	List<Product> findByTenantIdAndArchivedAtIsNullOrderByNameAscIdAsc(String tenantId);
	List<Product> findByTenantIdAndIdInAndArchivedAtIsNullOrderByNameAscIdAsc(String tenantId,
			Collection<String> ids);

	// Archived products: archivedAt filled.
	List<Product> findByTenantIdAndArchivedAtIsNotNullOrderByNameAscIdAsc(String tenantId);
	List<Product> findByTenantIdAndIdInAndArchivedAtIsNotNullOrderByNameAscIdAsc(String tenantId,
			Collection<String> ids);
}
