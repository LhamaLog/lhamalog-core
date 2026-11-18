package br.com.lhamalog.core.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import br.com.lhamalog.core.shared.CompanySession;

/**
 * Feature 009: when the context version cannot be written after the product write, the product write is undone
 * (no Mongo needed; the repositories are mocks).
 */
@ExtendWith(MockitoExtension.class)
class ProductContextRevisionCompensationTests {
	private static final Product.Context CONTEXT = new Product.Context("Processa pagamentos.", null, null, null, null, null);
	private static final Product.Context CHANGED = new Product.Context("Processa estornos.", null, null, null, null, null);

	@Mock ProductRepository products;
	@Mock ProductAccessRepository accesses;
	@Mock ProductContextRevisionRepository revisions;

	private ProductService service() {
		return new ProductService(products, accesses, revisions);
	}

	@Test
	void createRemovesProductAndAccessWhenTheFirstVersionFails() {
		CompanySession session = new CompanySession("org_a", "user_a", false);
		when(products.insert(any(Product.class))).thenAnswer(call -> call.getArgument(0));
		when(revisions.findFirstByProductIdOrderByRevisionDesc(any(String.class))).thenReturn(Optional.empty());
		when(revisions.insert(any(ProductContextRevision.class)))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		ProductController.CreateProduct request = new ProductController.CreateProduct("Pagamentos", null, null, null,
				CONTEXT);
		assertThrows(DataAccessResourceFailureException.class, () -> service().create(session, request));

		ArgumentCaptor<Product> inserted = ArgumentCaptor.forClass(Product.class);
		verify(products).insert(inserted.capture());
		String id = inserted.getValue().id();
		verify(products).deleteById(id);
		verify(accesses).deleteById(id + ":user_a");
	}

	@Test
	void updateContextPutsThePreviousContextBackWhenTheVersionFails() {
		CompanySession admin = new CompanySession("org_a", "admin_a", true);
		when(products.findByIdAndTenantId("p1", "org_a")).thenReturn(Optional.of(product(CONTEXT, 3L)));
		when(products.save(any(Product.class))).thenAnswer(call -> {
			Product written = call.getArgument(0);
			return withVersion(written, written.version() + 1);
		});
		when(revisions.findFirstByProductIdOrderByRevisionDesc("p1")).thenReturn(Optional.empty());
		when(revisions.insert(any(ProductContextRevision.class)))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		ProductController.UpdateContext request = new ProductController.UpdateContext(CHANGED, 3L);
		assertThrows(DataAccessResourceFailureException.class, () -> service().updateContext(admin, "p1", request));

		ArgumentCaptor<Product> written = ArgumentCaptor.forClass(Product.class);
		verify(products, times(2)).save(written.capture());
		List<Product> writes = written.getAllValues();
		assertEquals(CHANGED, writes.get(0).context());
		assertEquals(CONTEXT, writes.get(1).context());
		assertEquals(4L, writes.get(1).version());
	}

	@Test
	void failedUndoIsAttachedToTheOriginalFailure() {
		CompanySession admin = new CompanySession("org_a", "admin_a", true);
		when(products.findByIdAndTenantId("p1", "org_a")).thenReturn(Optional.of(product(CONTEXT, 3L)));
		when(products.save(any(Product.class)))
				.thenAnswer(call -> withVersion(call.getArgument(0), 4L))
				.thenThrow(new DataAccessResourceFailureException("undo failed"));
		when(revisions.findFirstByProductIdOrderByRevisionDesc("p1")).thenReturn(Optional.empty());
		when(revisions.insert(any(ProductContextRevision.class)))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		ProductController.UpdateContext request = new ProductController.UpdateContext(CHANGED, 3L);
		DataAccessResourceFailureException failure = assertThrows(DataAccessResourceFailureException.class,
				() -> service().updateContext(admin, "p1", request));

		assertEquals("database unavailable", failure.getMessage());
		assertEquals(1, failure.getSuppressed().length);
		assertEquals("undo failed", failure.getSuppressed()[0].getMessage());
	}

	private static Product product(Product.Context context, Long version) {
		return new Product("p1", "org_a", "Pagamentos", "pagamentos", "", "", "", "CONFIGURING",
				Instant.parse("2026-11-17T10:00:00Z"), null, context, version);
	}

	private static Product withVersion(Product product, Long version) {
		return new Product(product.id(), product.tenantId(), product.name(), product.normalizedName(),
				product.description(), product.category(), product.productVersion(), product.status(),
				product.createdAt(), product.archivedAt(), product.context(), version);
	}
}
