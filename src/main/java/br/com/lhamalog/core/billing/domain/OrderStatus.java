package br.com.lhamalog.core.billing.domain;

/** Lifecycle of a {@link SubscriptionOrder}: PENDING to CANCELED (newer order) or PAID (confirmation). PAID is final. */
public enum OrderStatus {

	PENDING,

	PAID,

	CANCELED

}
