package br.com.lhamalog.core.product;

import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

/**
 * Writes {@code productId}, equal to {@code _id}, on every saved product (feature 013, LLT-179). The Collector
 * (LLT-181) finds the product of an ingestion token with {@code products.productId}; the field lives only in the
 * stored document, so the {@link Product} record and its constructor stay unchanged.
 */
@Component
class ProductCollectorFields implements BeforeSaveCallback<Product> {
	static final String PRODUCT_ID = "productId";

	@Override
	public Product onBeforeSave(Product product, Document document, String collection) {
		document.put(PRODUCT_ID, product.id());
		return product;
	}
}
