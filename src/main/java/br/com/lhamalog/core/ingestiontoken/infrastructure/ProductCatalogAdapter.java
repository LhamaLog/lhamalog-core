package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.util.Optional;
import br.com.lhamalog.core.ingestiontoken.application.ProductCatalog;
import br.com.lhamalog.core.product.ProductRepository;
import org.springframework.stereotype.Component;

/** Reads the product of the company from the product module (collection {@code products}, feature 013). */
@Component
public class ProductCatalogAdapter implements ProductCatalog {
	private final ProductRepository products;

	public ProductCatalogAdapter(ProductRepository products) {
		this.products = products;
	}

	@Override
	public Optional<ProductRef> find(String tenantId, String productId) {
		return products.findByIdAndTenantId(productId, tenantId)
				.map(product -> new ProductRef(product.id(), product.tenantId(), product.archivedAt() != null));
	}
}
