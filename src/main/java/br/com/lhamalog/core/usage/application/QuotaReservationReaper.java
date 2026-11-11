package br.com.lhamalog.core.usage.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Returns reservations left unconfirmed after their bounded lifetime. */
@Component
public class QuotaReservationReaper {
	private final QuotaReservationService reservations;

	public QuotaReservationReaper(QuotaReservationService reservations) {
		this.reservations = reservations;
	}

	@Scheduled(fixedDelayString = "${lhamalog.usage.reservation-reaper-interval:30s}")
	public void reapExpiredReservations() {
		reservations.expireReservations();
	}
}
