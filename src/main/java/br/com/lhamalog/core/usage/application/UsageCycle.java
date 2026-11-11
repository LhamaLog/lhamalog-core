package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

public record UsageCycle(Instant from, Instant to) {
	/**
	 * Technical default, reversible pending product confirmation: there is no active
	 * cycle before {@code firstPaymentDate}. The method returns empty instead of a
	 * future cycle. The external source of {@code firstPaymentDate} remains undefined.
	 */
	public static Optional<UsageCycle> calculate(PlanType planType, LocalDate firstPaymentDate, Instant now) {
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		if (today.isBefore(firstPaymentDate)) {
			return Optional.empty();
		}
		if (planType == PlanType.WEEKLY) {
			long weeks = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(firstPaymentDate, today) / 7);
			LocalDate from = firstPaymentDate.plusWeeks(weeks);
			return Optional.of(utc(from, from.plusWeeks(1)));
		}
		int month = 0;
		while (!monthlyAnchor(firstPaymentDate, month + 1).isAfter(today)) {
			month++;
		}
		LocalDate from = monthlyAnchor(firstPaymentDate, month);
		return Optional.of(utc(from, monthlyAnchor(firstPaymentDate, month + 1)));
	}

	private static LocalDate monthlyAnchor(LocalDate firstPaymentDate, int monthOffset) {
		YearMonth month = YearMonth.from(firstPaymentDate).plusMonths(monthOffset);
		return month.atDay(Math.min(firstPaymentDate.getDayOfMonth(), month.lengthOfMonth()));
	}

	private static UsageCycle utc(LocalDate from, LocalDate to) {
		return new UsageCycle(from.atStartOfDay().toInstant(ZoneOffset.UTC),
				to.atStartOfDay().toInstant(ZoneOffset.UTC));
	}
}
