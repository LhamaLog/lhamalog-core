package br.com.lhamalog.core.product;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import br.com.lhamalog.core.shared.CompanySession;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {
	private final ProductRepository products;
	private final ProductAccessRepository accesses;
	public ProductService(ProductRepository products, ProductAccessRepository accesses) {
		this.products = products;
		this.accesses = accesses;
	}

	public List<Product> list(CompanySession session) {
		if (session.administrator()) return products.findByTenantIdOrderByNameAscIdAsc(session.tenantId());
		List<String> ids = accesses.findByTenantIdAndClerkUserId(session.tenantId(), session.userId())
				.stream().map(ProductAccess::productId).toList();
		return products.findByTenantIdAndIdInOrderByNameAscIdAsc(session.tenantId(), ids);
	}

	public Product get(CompanySession session, String id) {
		Product product = products.findByIdAndTenantId(id, session.tenantId()).orElseThrow(this::notFound);
		if (!session.administrator() && !accesses.existsByTenantIdAndProductIdAndClerkUserId(
				session.tenantId(), id, session.userId())) throw notFound();
		return product;
	}

	public Product create(CompanySession session, ProductController.CreateProduct request) {
		String id = UUID.randomUUID().toString();
		ProductAccess access = new ProductAccess(id + ":" + session.userId(), session.tenantId(), id, session.userId());
		accesses.insert(access);
		try {
			return products.insert(new Product(id, session.tenantId(), request.name().trim(),
					request.description(), request.category(), request.productVersion(), "CONFIGURING",
					Instant.now(), request.context(), null));
		}
		catch (RuntimeException exception) {
			try { accesses.deleteById(access.id()); }
			catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
			throw exception;
		}
	}

	public Product updateContext(CompanySession session, String id, ProductController.UpdateContext request) {
		Product current = get(session, id);
		if (!Objects.equals(current.version(), request.version())) {
			throw new OptimisticLockingFailureException("Product version conflict");
		}
		return products.save(new Product(current.id(), current.tenantId(), current.name(), current.description(),
				current.category(), current.productVersion(), current.status(), current.createdAt(),
				request.context(), current.version()));
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado ou sem acesso.");
	}
}
