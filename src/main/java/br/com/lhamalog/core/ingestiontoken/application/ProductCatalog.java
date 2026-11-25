package br.com.lhamalog.core.ingestiontoken.application;

import java.util.Optional;

/** Port to the product module: whether a product belongs to the company and whether it is archived. */
public interface ProductCatalog {
	Optional<ProductRef> find(String tenantId, String productId);

	record ProductRef(String id, String tenantId, boolean archived) {}
}
