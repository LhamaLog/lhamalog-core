package br.com.lhamalog.core.usage.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries durable notification intents after a transient stream failure. */
@Component
public class NotificationOutboxPublisher {
	private final LowBalanceNotificationService notifications;

	public NotificationOutboxPublisher(LowBalanceNotificationService notifications) {
		this.notifications = notifications;
	}

	@Scheduled(fixedDelayString = "${lhamalog.notifications.outbox-publish-interval:30s}")
	public void publishPending() {
		notifications.publishPending();
	}
}
