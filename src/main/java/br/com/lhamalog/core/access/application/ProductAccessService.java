package br.com.lhamalog.core.access.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.product.ProductAccess;
import br.com.lhamalog.core.product.ProductAccessRepository;
import br.com.lhamalog.core.product.ProductRepository;
import br.com.lhamalog.core.shared.ApiStatusException;

/**
 * Which products a member can access (feature 011, US2, USID_030.1). Works on the existing {@code AcessoProduto}
 * collection without changing its format; the product list and detail already filter by it for members.
 */
@Service
public class ProductAccessService {

	static final String PRODUCT_NOT_FOUND = "Produto não encontrado nesta empresa.";

	static final String PRODUCT_NOT_FOUND_CODE = "product_not_found";

	static final String FULL_ACCESS = "Este membro acessa todos os Produtos.";

	static final String FULL_ACCESS_CODE = "full_access";

	private static final Logger log = LoggerFactory.getLogger(ProductAccessService.class);

	private final ProductAccessRepository accesses;

	private final ProductRepository products;

	private final AccessService access;

	public ProductAccessService(ProductAccessRepository accesses, ProductRepository products, AccessService access) {
		this.accesses = accesses;
		this.products = products;
		this.access = access;
	}

	/** Accesses of a person. The owner and administrators see everything, so their list is empty. */
	public MemberProducts list(String tenantId, String userId) {
		String id = UserIds.require(userId);
		if (access.hasFullAccess(tenantId, id)) {
			return MemberProducts.full(id);
		}
		return new MemberProducts(id, false, current(tenantId, id));
	}

	/**
	 * Replaces the accesses of a member by the given list: adds what is missing, removes what is left, never duplicates.
	 * Nothing is written when a product is not of the company. No transaction is needed: running it again gives the
	 * same final state.
	 */
	public MemberProducts replace(String tenantId, String userId, List<String> productIds) {
		String id = UserIds.require(userId);
		if (access.hasFullAccess(tenantId, id)) {
			throw new ApiStatusException(HttpStatus.CONFLICT, FULL_ACCESS, FULL_ACCESS_CODE);
		}
		Set<String> wanted = new LinkedHashSet<>(productIds);
		if (!wanted.isEmpty() && products.countByTenantIdAndIdIn(tenantId, wanted) != wanted.size()) {
			throw new ApiStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND, PRODUCT_NOT_FOUND_CODE);
		}
		List<String> existing = current(tenantId, id);
		List<String> toAdd = wanted.stream().filter((productId) -> !existing.contains(productId)).toList();
		List<String> toRemove = existing.stream().filter((productId) -> !wanted.contains(productId)).toList();
		for (String productId : toAdd) {
			try {
				// Same id as ProductService.create, so the unique index also guards against concurrent writes.
				accesses.insert(new ProductAccess(productId + ":" + id, tenantId, productId, id));
			}
			catch (DuplicateKeyException alreadyThere) {
				// A concurrent replace or the product creation wrote it first: the final state is the same.
			}
		}
		if (!toRemove.isEmpty()) {
			accesses.deleteByTenantIdAndClerkUserIdAndProductIdIn(tenantId, id, toRemove);
		}
		log.info("event=product_access.updated | Product accesses of a member replaced | tenantId={} userHash={} added={} removed={}",
				tenantId, UserHash.of(id), toAdd.size(), toRemove.size());
		return new MemberProducts(id, false, current(tenantId, id));
	}

	private List<String> current(String tenantId, String userId) {
		List<String> ids = new ArrayList<>(accesses.findByTenantIdAndClerkUserId(tenantId, userId).stream()
			.map(ProductAccess::productId)
			.toList());
		ids.sort(null);
		return ids;
	}

	/** Accesses of one person: {@code allProducts} for the owner and administrators. */
	public record MemberProducts(String userId, boolean allProducts, List<String> productIds) {

		static MemberProducts full(String userId) {
			return new MemberProducts(userId, true, List.of());
		}

	}

}
