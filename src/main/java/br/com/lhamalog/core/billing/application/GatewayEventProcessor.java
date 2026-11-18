package br.com.lhamalog.core.billing.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.lhamalog.core.billing.application.SubscriptionActivationService.Outcome;
import br.com.lhamalog.core.billing.application.port.out.GatewayEventRepository;
import br.com.lhamalog.core.billing.domain.GatewayEvent;
import br.com.lhamalog.core.billing.domain.GatewayEventStatus;
import br.com.lhamalog.core.billing.domain.GatewayNotification;

/**
 * Applies an authentic gateway event at most once per gateway event id (EventoGateway, ADR_125; research R4). A final
 * event (EXECUTED or IGNORED) is never processed again; a FAILED one is processed again when the gateway resends it.
 */
public class GatewayEventProcessor {

	static final String EVENT_NOT_HANDLED = "event_not_handled";

	private static final Logger log = LoggerFactory.getLogger(GatewayEventProcessor.class);

	private final GatewayEventRepository events;

	private final SubscriptionActivationService activation;

	private final Clock clock;

	public GatewayEventProcessor(GatewayEventRepository events, SubscriptionActivationService activation,
			Clock clock) {
		this.events = events;
		this.activation = activation;
		this.clock = clock;
	}

	/**
	 * @return how the delivery ends for the gateway
	 * @throws RuntimeException when processing failed; the event stays FAILED and a resend processes it again
	 */
	public Result process(GatewayNotification notification) {
		Optional<Result> skipped = claim(notification);
		if (skipped.isPresent()) {
			return skipped.get();
		}
		Outcome outcome;
		try {
			outcome = handle(notification);
		}
		catch (RuntimeException ex) {
			events.complete(notification.eventId(), GatewayEventStatus.FAILED, ex.getClass().getSimpleName(), null,
					clock.instant());
			log.error("event=billing.activation.failed | Gateway event processing failed; waiting for a resend"
					+ " | eventId={} type={} orderId={} error={}", notification.eventId(), notification.type(),
					notification.orderId(), ex.getClass().getSimpleName());
			throw ex;
		}
		events.complete(notification.eventId(), outcome.status(), outcome.reason(), outcome.tenantId(),
				clock.instant());
		return Result.PROCESSED;
	}

	/** Records the event, or tells why it must not be processed now. */
	private Optional<Result> claim(GatewayNotification notification) {
		Optional<GatewayEvent> existing = events.findById(notification.eventId());
		if (existing.isEmpty()) {
			if (events.insertReceived(GatewayEvent.received(notification, clock.instant()))) {
				return Optional.empty();
			}
			existing = events.findById(notification.eventId());
		}
		GatewayEventStatus status = existing.map(GatewayEvent::status).orElse(GatewayEventStatus.RECEIVED);
		if (status.isFinal()) {
			log.info("event=billing.webhook.duplicate | Gateway event already processed | eventId={} status={}",
					notification.eventId(), status);
			return Optional.of(Result.DUPLICATE);
		}
		if (status == GatewayEventStatus.FAILED && events.claimRetry(notification.eventId(), clock.instant())) {
			return Optional.empty();
		}
		log.info("event=billing.webhook.in_progress | Gateway event is being processed by another delivery"
				+ " | eventId={}", notification.eventId());
		return Optional.of(Result.IN_PROGRESS);
	}

	private Outcome handle(GatewayNotification notification) {
		if (!notification.confirmsPayment()) {
			log.info("event=billing.webhook.ignored | Gateway event type not handled | eventId={} type={}",
					notification.eventId(), notification.type());
			return new Outcome(GatewayEventStatus.IGNORED, EVENT_NOT_HANDLED, null);
		}
		return activation.activate(notification);
	}

	public enum Result {

		/** Processed now (executed, ignored or recorded as failed without retry). */
		PROCESSED,

		/** Already processed before. */
		DUPLICATE,

		/** Another delivery of the same event is being processed. */
		IN_PROGRESS

	}

}
