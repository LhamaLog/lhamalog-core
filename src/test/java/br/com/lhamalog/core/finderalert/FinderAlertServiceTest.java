package br.com.lhamalog.core.finderalert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductRepository;

/** Spec 016: first write notifies once; a repeat and a foreign product do not. */
class FinderAlertServiceTest {

	private final List<FinderAlertCommand> notices = new ArrayList<>();

	private final ProductRepository products = mock(ProductRepository.class);

	private boolean inserted = true;

	private int inserts;

	private final FinderAlertService service = new FinderAlertService(products, command -> {
		inserts++;
		return inserted;
	}, notices::add);

	private static final FinderAlertCommand COMMAND = new FinderAlertCommand("t", "p", "b",
			FinderAlertCommand.OPENOBSERVE_UNAVAILABLE);

	@Test
	void firstWriteNotifiesAndRepeatDoesNot() {
		when(products.findByIdAndTenantId("p", "t")).thenReturn(Optional.of(product()));
		FinderAlertService.Result first = service.accept(COMMAND);
		assertThat(first.created()).isTrue();
		assertThat(first.body().aceito()).isTrue();
		assertThat(first.body().tenantId()).isEqualTo("t");
		assertThat(notices).containsExactly(COMMAND);

		inserted = false;
		FinderAlertService.Result again = service.accept(COMMAND);
		assertThat(again.created()).isFalse();
		assertThat(again.body()).isEqualTo(first.body());
		assertThat(notices).containsExactly(COMMAND);
		assertThat(inserts).isEqualTo(2);
	}

	@Test
	void aProductOfAnotherTenantIsRefusedBeforeWriting() {
		when(products.findByIdAndTenantId("p", "t")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.accept(COMMAND)).isInstanceOfSatisfying(InternalApiException.class,
				refused -> assertThat(refused.code()).isEqualTo(InternalApiException.ACCESS_DENIED));
		assertThat(inserts).isZero();
		assertThat(notices).isEmpty();
	}

	@Test
	void aFailedNoticeDoesNotUndoTheWrite() {
		when(products.findByIdAndTenantId("p", "t")).thenReturn(Optional.of(product()));
		FinderAlertService failing = new FinderAlertService(products, command -> true, command -> {
			throw new IllegalStateException("socket fechado");
		});
		assertThat(failing.accept(COMMAND).created()).isTrue();
	}

	private static Product product() {
		return new Product("p", "t", "Produto", "produto", null, null, null, null, null, null, null, null);
	}

}
