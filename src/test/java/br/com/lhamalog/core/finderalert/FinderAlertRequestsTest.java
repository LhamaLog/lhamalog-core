package br.com.lhamalog.core.finderalert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.occurrence.application.InternalApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 016: the alert body accepts only the four fields and the one reason. */
class FinderAlertRequestsTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void readsTheAcceptedReason() {
		FinderAlertCommand command = FinderAlertRequests.read(json("""
				{"tenantId":"t","productId":"p","batchId":"b","motivo":"consulta_openobserve_indisponivel"}
				"""));
		assertThat(command).isEqualTo(new FinderAlertCommand("t", "p", "b", "consulta_openobserve_indisponivel"));
	}

	@Test
	void rejectsAnIncompleteBodyAnExtraFieldOrAnotherReason() {
		assertInvalid("""
				{"tenantId":"t","productId":"p","batchId":"b"}
				""");
		assertInvalid("""
				{"tenantId":"t","productId":"p","batchId":"b","motivo":"consulta_openobserve_indisponivel","extra":"x"}
				""");
		assertInvalid("""
				{"tenantId":"t","productId":"p","batchId":"b","motivo":"outro"}
				""");
		assertInvalid("""
				{"tenantId":" ","productId":"p","batchId":"b","motivo":"consulta_openobserve_indisponivel"}
				""");
	}

	@Test
	void rejectsABodyThatIsNotAnObject() {
		assertThatThrownBy(() -> FinderAlertRequests.read(json("[]")))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_REQUEST));
		assertThatThrownBy(() -> FinderAlertRequests.read(null))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_REQUEST));
	}

	private void assertInvalid(String body) {
		assertThatThrownBy(() -> FinderAlertRequests.read(json(body)))
			.isInstanceOfSatisfying(InternalApiException.class,
					refused -> assertThat(refused.code()).isEqualTo(InternalApiException.INVALID_DATA));
	}

	private JsonNode json(String body) {
		return mapper.readTree(body);
	}

}
