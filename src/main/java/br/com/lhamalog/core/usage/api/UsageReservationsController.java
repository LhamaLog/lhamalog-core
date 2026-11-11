package br.com.lhamalog.core.usage.api;

import br.com.lhamalog.core.usage.application.QuotaReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/usage-reservations")
public class UsageReservationsController {
	private final QuotaReservationService reservations;

	public UsageReservationsController(QuotaReservationService reservations) {
		this.reservations = reservations;
	}

	@PostMapping
	public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
		QuotaReservationService.ReservationResponse response = reservations.reserve(
				new QuotaReservationService.ReservationRequest(request.reservationId(), request.tenantId(), request.productId(),
						request.requestedBytes(), request.batchId(), request.domain()));
		return switch (response.outcome()) {
			case AUTHORIZED, AUTHORIZED_UNLIMITED -> ResponseEntity.status(response.replay() ? HttpStatus.OK : HttpStatus.CREATED)
					.body(new ReservationResponse(response.outcome().name(), response.reservationId(), response.expiresAt()));
			case INSUFFICIENT_BALANCE -> ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ReservationResponse("INSUFFICIENT_BALANCE", null, null));
			case NO_PLAN -> ResponseEntity.unprocessableEntity().body(new ReservationResponse("NO_PLAN", null, null));
		};
	}

	@PostMapping("/{reservationId}/confirm")
	public ResponseEntity<Void> confirm(@PathVariable @NotBlank String reservationId) {
		return transition(reservations.confirm(reservationId), "RESERVATION_NOT_ACTIVE");
	}

	@PostMapping("/{reservationId}/release")
	public ResponseEntity<Void> release(@PathVariable @NotBlank String reservationId) {
		return transition(reservations.release(reservationId), "RESERVATION_NOT_ACTIVE");
	}

	@PostMapping("/{reservationId}/refund")
	public ResponseEntity<Void> refund(@PathVariable @NotBlank String reservationId) {
		return transition(reservations.refund(reservationId), "RESERVATION_NOT_REFUNDABLE");
	}

	@GetMapping("/balance")
	public ResponseEntity<BalanceResponse> balance(@RequestParam @NotBlank String tenantId,
			@RequestParam @NotBlank String productId) {
		return reservations.balance(tenantId, productId)
				.map(value -> ResponseEntity.ok(new BalanceResponse(value.allowanceBytes(), value.reservedBytes(),
						value.consumedBytes(), value.remainingBytes(), value.cycleStart())))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private static ResponseEntity<Void> transition(QuotaReservationService.TransitionResult result, String conflictCode) {
		return switch (result) {
			case OK -> ResponseEntity.noContent().build();
			case NOT_FOUND -> ResponseEntity.notFound().build();
			case NOT_ACTIVE, NOT_REFUNDABLE -> ResponseEntity.status(HttpStatus.CONFLICT)
					.header("X-LhamaLog-Error-Code", conflictCode).build();
		};
	}

	public record ReservationRequest(@NotBlank String reservationId, @NotBlank String tenantId, @NotBlank String productId,
			@Positive long requestedBytes, String batchId, @NotNull UsageDomain domain) {
	}

	public record ReservationResponse(String outcome, String reservationId, Instant expiresAt) {
	}

	public record BalanceResponse(long allowanceBytes, long reservedBytes, long consumedBytes, long remainingBytes,
			Instant cycleStart) {
	}
}
