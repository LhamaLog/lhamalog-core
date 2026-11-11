package br.com.lhamalog.core.usage.application;

/** Delivery port for notifications already made durable in the outbox. */
public interface LowBalanceNotificationPublisher {
	void publish(LowBalanceNotification notification);
}
