package br.com.lhamalog.core.product;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One saved version of a product context (feature 009). Numbered per product (1, 2, 3...) and never changed
 * after it is written. Same design as the company context revisions (TenantContextRevision, LLT-176).
 */
@Document("ProductContextRevision")
@CompoundIndex(name = "product_context_revision", def = "{'productId': 1, 'revision': 1}", unique = true)
public record ProductContextRevision(@Id String id, String tenantId, String productId, long revision,
		Instant createdAt, String actorUserId, Product.Context context) {

	public static ProductContextRevision of(Product product, long revision, Instant createdAt, String actorUserId) {
		return new ProductContextRevision(product.id() + ":" + revision, product.tenantId(), product.id(), revision,
				createdAt, actorUserId, product.context());
	}

	/** Response body of one version: no tenant or product ids, which the caller already knows. */
	public record View(long revision, Instant createdAt, String actorUserId, Product.Context context) {
		static View from(ProductContextRevision stored) {
			return new View(stored.revision(), stored.createdAt(), stored.actorUserId(), stored.context());
		}
	}

	/** Response body of the version list, newest first. */
	public record ViewList(List<View> revisions) {
		static ViewList from(List<ProductContextRevision> stored) {
			return new ViewList(stored.stream().map(View::from).toList());
		}
	}
}
