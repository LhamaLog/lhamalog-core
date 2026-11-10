package br.com.lhamalog.core.usage.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

public record UsageCycle(Instant from, Instant to) {
	public static UsageCycle calculate(PlanType planType, LocalDate firstPaymentDate, Instant now) {
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		if (planType == PlanType.WEEKLY) {
			long weeks = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(firstPaymentDate, today) / 7);
			LocalDate from = firstPaymentDate.plusWeeks(weeks);
			return utc(from, from.plusWeeks(1));
		}
		int month = 0;
		while (!monthlyAnchor(firstPaymentDate, month + 1).isAfter(today)) month++;
		LocalDate from = monthlyAnchor(firstPaymentDate, month);
		return utc(from, monthlyAnchor(firstPaymentDate, month + 1));
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
