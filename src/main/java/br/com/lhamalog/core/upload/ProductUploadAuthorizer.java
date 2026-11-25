package br.com.lhamalog.core.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductAccessRepository;
import br.com.lhamalog.core.product.ProductRepository;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;

/**
 * Visibility rule of {@code GET /api/products/{id}} (admin sees every product of the tenant; others need
 * ProductAccess), plus a refusal of archived products. Failures are 403, including unknown ids (LLT-292 Q11).
 */
@Component
public class ProductUploadAuthorizer {

	static final String PRODUCT_FORBIDDEN = "product_forbidden";

	private static final Logger log = LoggerFactory.getLogger(ProductUploadAuthorizer.class);

	private final ProductRepository products;

	private final ProductAccessRepository accesses;

	public ProductUploadAuthorizer(ProductRepository products, ProductAccessRepository accesses) {
		this.products = products;
		this.accesses = accesses;
	}

	public void requireUploadable(CompanySession session, String productId) {
		Product product = products.findByIdAndTenantId(productId, session.tenantId()).orElse(null);
		boolean visible = product != null && (session.administrator()
				|| accesses.existsByTenantIdAndProductIdAndClerkUserId(session.tenantId(), productId, session.userId()));
		boolean archived = product != null && product.archivedAt() != null;
		if (!visible || archived) {
			log.info(
					"event=manual_upload.product_forbidden | Product is not uploadable for this session | productId={} tenantId={} archived={}",
					productId, session.tenantId(), archived);
			throw new ApiStatusException(HttpStatus.FORBIDDEN,
					"Produto não encontrado, sem acesso ou arquivado.", PRODUCT_FORBIDDEN);
		}
	}
}
