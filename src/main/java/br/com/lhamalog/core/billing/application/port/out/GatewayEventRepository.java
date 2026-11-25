package br.com.lhamalog.core.billing.application.port.out;

import java.time.Instant;
import java.util.Optional;

import br.com.lhamalog.core.billing.domain.GatewayEvent;
import br.com.lhamalog.core.billing.domain.GatewayEventStatus;

/** Output port for gateway events (EventoGateway, ADR_125). Writes are conditional so duplicates are harmless. */
public interface GatewayEventRepository {

	Optional<GatewayEvent> findById(String eventId);

	/** @return {@code false} when an event with the same id already exists */
	boolean insertReceived(GatewayEvent event);

	/**
	 * Takes the event back to RECEIVED and counts one more attempt, when it is FAILED or when it has been RECEIVED
	 * since before {@code staleBefore} (the delivery that took it stopped without recording the result).
	 * @return {@code false} when another delivery holds the event or it is final
	 */
	boolean claimRetry(String eventId, Instant now, Instant staleBefore);

	/** Records the result of the processing. */
	void complete(String eventId, GatewayEventStatus status, String reason, String tenantId, Instant now);

}
