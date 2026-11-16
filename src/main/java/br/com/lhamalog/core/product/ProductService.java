package br.com.lhamalog.core.product;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

	public List<Product> list(CompanySession session) { return list(session, false); }

	/** Lists active products by default; {@code archived = true} lists only archived ones. */
	public List<Product> list(CompanySession session, boolean archived) {
		String tenantId = session.tenantId();
		if (session.administrator()) {
			return archived ? products.findByTenantIdAndArchivedAtIsNotNullOrderByNameAscIdAsc(tenantId)
					: products.findByTenantIdAndArchivedAtIsNullOrderByNameAscIdAsc(tenantId);
		}
		List<String> ids = accesses.findByTenantIdAndClerkUserId(tenantId, session.userId())
				.stream().map(ProductAccess::productId).toList();
		return archived ? products.findByTenantIdAndIdInAndArchivedAtIsNotNullOrderByNameAscIdAsc(tenantId, ids)
				: products.findByTenantIdAndIdInAndArchivedAtIsNullOrderByNameAscIdAsc(tenantId, ids);
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
					Instant.now(), null, request.context(), null));
		}
		catch (RuntimeException exception) {
			try { accesses.deleteById(access.id()); }
			catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
			throw exception;
		}
	}

	/** Updates name, description, category and business version. Context, status and archive state stay as they are. */
	public Product update(CompanySession session, String id, ProductController.UpdateProduct request) {
		Product current = get(session, id);
		requireActive(current);
		requireCurrentVersion(current, request.version());
		return products.save(new Product(current.id(), current.tenantId(), request.name().trim(),
				request.description(), request.category(), request.productVersion(), current.status(),
				current.createdAt(), current.archivedAt(), current.context(), current.version()));
	}

	public Product updateContext(CompanySession session, String id, ProductController.UpdateContext request) {
		Product current = get(session, id);
		requireActive(current);
		requireCurrentVersion(current, request.version());
		return products.save(new Product(current.id(), current.tenantId(), current.name(), current.description(),
				current.category(), current.productVersion(), current.status(), current.createdAt(),
				current.archivedAt(), request.context(), current.version()));
	}

	/** Owners/admins only. Archiving an already archived product changes nothing. */
	public Product archive(CompanySession session, String id, Long version) {
		Product current = get(session, id);
		requireAdministrator(session);
		if (current.archivedAt() != null) return current;
		requireCurrentVersion(current, version);
		// MongoDB stores dates in milliseconds; truncating keeps the response equal to what is persisted.
		return products.save(withArchivedAt(current, Instant.now().truncatedTo(ChronoUnit.MILLIS)));
	}

	/** Owners/admins only. Unarchiving an active product changes nothing. */
	public Product unarchive(CompanySession session, String id, Long version) {
		Product current = get(session, id);
		requireAdministrator(session);
		if (current.archivedAt() == null) return current;
		requireCurrentVersion(current, version);
		return products.save(withArchivedAt(current, null));
	}

	private static Product withArchivedAt(Product current, Instant archivedAt) {
		return new Product(current.id(), current.tenantId(), current.name(), current.description(),
				current.category(), current.productVersion(), current.status(), current.createdAt(),
				archivedAt, current.context(), current.version());
	}

	private static void requireActive(Product product) {
		if (product.archivedAt() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto arquivado não pode ser editado.");
		}
	}

	private static void requireCurrentVersion(Product product, Long version) {
		if (!Objects.equals(product.version(), version)) {
			throw new OptimisticLockingFailureException("Product version conflict");
		}
	}

	private static void requireAdministrator(CompanySession session) {
		if (!session.administrator()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Somente donos e administradores podem arquivar ou desarquivar produtos.");
		}
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado ou sem acesso.");
	}
}
