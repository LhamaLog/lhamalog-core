package br.com.lhamalog.core.usage.application;

/** Lifecycle states of an idempotent ingestion quota reservation. */
public enum ReservationState {
	RESERVED,
	CONFIRMED,
	REFUNDED,
	RELEASED,
	EXPIRED
}
