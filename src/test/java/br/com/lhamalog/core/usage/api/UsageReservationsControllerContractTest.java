package br.com.lhamalog.core.usage.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lhamalog.core.config.UsageFactsSecurityConfiguration;
import br.com.lhamalog.core.usage.application.QuotaReservationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = UsageReservationsController.class, properties = {
		"LHAMALOG_CORE_USAGE_TOKEN=test-token",
		"management.otlp.metrics.export.enabled=false",
		"management.tracing.export.otlp.enabled=false" })
@Import(UsageFactsSecurityConfiguration.class)
class UsageReservationsControllerContractTest {
	private static final String TOKEN = "test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private QuotaReservationService reservations;

	@Test
	void requiresOnlyTheServiceTokenForEveryReservationRoute() throws Exception {
		mockMvc.perform(get("/internal/usage-reservations/balance").param("tenantId", "tenant").param("productId", "product"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/usage-reservations/reservation/confirm").header("Authorization", "Bearer token"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", "wrong")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/usage-reservations/reservation/release"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/usage-reservations/reservation/refund")
				.header("Authorization", "Bearer token"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/internal/usage-reservations/balance").header("Authorization", "Bearer token")
				.param("tenantId", "tenant").param("productId", "product"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void mapsReserveTransitionAndBalanceContractStatuses() throws Exception {
		when(reservations.reserve(any())).thenReturn(new QuotaReservationService.ReservationResponse(
				QuotaReservationService.Outcome.AUTHORIZED, "reservation", Instant.parse("2026-11-11T12:20:00Z"), false));
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"reservation","tenantId":"tenant","productId":"product",
						"requestedBytes":100,"domain":"LOGS"}"""))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("AUTHORIZED"))
				.andExpect(jsonPath("$.reservationId").value("reservation"));

		when(reservations.reserve(any())).thenReturn(new QuotaReservationService.ReservationResponse(
				QuotaReservationService.Outcome.AUTHORIZED_UNLIMITED, "reservation-u", Instant.parse("2026-11-11T12:20:00Z"),
				false));
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"reservation-u","tenantId":"tenant","productId":"product",
						"requestedBytes":100,"domain":"LOGS"}"""))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("AUTHORIZED_UNLIMITED"));

		when(reservations.reserve(any())).thenReturn(new QuotaReservationService.ReservationResponse(
				QuotaReservationService.Outcome.AUTHORIZED, "reservation", Instant.parse("2026-11-11T12:20:00Z"), true));
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"reservation","tenantId":"tenant","productId":"product",
						"requestedBytes":100,"domain":"LOGS"}"""))
				.andExpect(status().isOk());

		when(reservations.reserve(any())).thenReturn(new QuotaReservationService.ReservationResponse(
				QuotaReservationService.Outcome.INSUFFICIENT_BALANCE, null, null, false));
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"reservation-2","tenantId":"tenant","productId":"product",
						"requestedBytes":100,"domain":"LOGS"}"""))
				.andExpect(status().isConflict());

		when(reservations.reserve(any())).thenReturn(new QuotaReservationService.ReservationResponse(
				QuotaReservationService.Outcome.NO_PLAN, null, null, false));
		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"reservation-3","tenantId":"tenant","productId":"product",
						"requestedBytes":100,"domain":"LOGS"}"""))
				.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(post("/internal/usage-reservations").header("X-LhamaLog-Service-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reservationId":"","tenantId":"tenant","productId":"product",
						"requestedBytes":0}"""))
				.andExpect(status().isBadRequest());

		when(reservations.balance("tenant", "product")).thenReturn(Optional.of(new QuotaReservationService.BalanceResponse(
				10_000, 100, 2_000, 7_900, Instant.parse("2026-11-01T00:00:00Z"))));
		mockMvc.perform(get("/internal/usage-reservations/balance").header("X-LhamaLog-Service-Token", TOKEN)
				.param("tenantId", "tenant").param("productId", "product"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.allowanceBytes").value(10_000))
				.andExpect(jsonPath("$.reservedBytes").value(100)).andExpect(jsonPath("$.consumedBytes").value(2_000))
				.andExpect(jsonPath("$.remainingBytes").value(7_900)).andExpect(jsonPath("$.cycleStart").value("2026-11-01T00:00:00Z"));

		when(reservations.balance("tenant", "product")).thenReturn(Optional.empty());
		mockMvc.perform(get("/internal/usage-reservations/balance").header("X-LhamaLog-Service-Token", TOKEN)
				.param("tenantId", "tenant").param("productId", "product"))
				.andExpect(status().isNotFound());
	}

	@Test
	void mapsConfirmReleaseAndRefundSuccessNotFoundAndConflictStatuses() throws Exception {
		when(reservations.confirm("confirmed")).thenReturn(QuotaReservationService.TransitionResult.OK);
		when(reservations.confirm("confirm-missing")).thenReturn(QuotaReservationService.TransitionResult.NOT_FOUND);
		when(reservations.confirm("confirm-inactive")).thenReturn(QuotaReservationService.TransitionResult.NOT_ACTIVE);
		when(reservations.release("released")).thenReturn(QuotaReservationService.TransitionResult.OK);
		when(reservations.release("release-missing")).thenReturn(QuotaReservationService.TransitionResult.NOT_FOUND);
		when(reservations.release("release-inactive")).thenReturn(QuotaReservationService.TransitionResult.NOT_ACTIVE);
		when(reservations.refund("refunded")).thenReturn(QuotaReservationService.TransitionResult.OK);
		when(reservations.refund("refund-missing")).thenReturn(QuotaReservationService.TransitionResult.NOT_FOUND);
		when(reservations.refund("not-refundable")).thenReturn(QuotaReservationService.TransitionResult.NOT_REFUNDABLE);

		expectTransition("confirmed", "confirm", status().isNoContent(), null);
		expectTransition("confirm-missing", "confirm", status().isNotFound(), null);
		expectTransition("confirm-inactive", "confirm", status().isConflict(), "RESERVATION_NOT_ACTIVE");
		expectTransition("released", "release", status().isNoContent(), null);
		expectTransition("release-missing", "release", status().isNotFound(), null);
		expectTransition("release-inactive", "release", status().isConflict(), "RESERVATION_NOT_ACTIVE");
		expectTransition("refunded", "refund", status().isNoContent(), null);
		expectTransition("refund-missing", "refund", status().isNotFound(), null);
		expectTransition("not-refundable", "refund", status().isConflict(), "RESERVATION_NOT_REFUNDABLE");
	}

	private void expectTransition(String reservationId, String operation,
			org.springframework.test.web.servlet.ResultMatcher expectedStatus, String errorCode) throws Exception {
		var result = mockMvc.perform(post("/internal/usage-reservations/{reservationId}/{operation}", reservationId, operation)
				.header("X-LhamaLog-Service-Token", TOKEN)).andExpect(expectedStatus);
		if (errorCode != null) {
			result.andExpect(header().string("X-LhamaLog-Error-Code", errorCode));
		}
	}
}
