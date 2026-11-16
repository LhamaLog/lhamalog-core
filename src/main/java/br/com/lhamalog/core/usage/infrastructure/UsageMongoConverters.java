package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.PlanTier;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Persists {@link LocalDate} values at UTC midnight so a seed such as
 * {@code 2026-11-01T00:00:00Z} is read back as 2026-11-01 regardless of the
 * JVM default time zone. Usage cycles are also calculated in UTC.
 *
 * <p>Also maps {@link PlanTier} case-insensitively for ADR_131 English names
 * (e.g. {@code Starter} → {@link PlanTier#STARTER}).
 */
@Configuration
public class UsageMongoConverters {

	@Bean
	MongoCustomConversions mongoCustomConversions() {
		return utc();
	}

	public static MongoCustomConversions utc() {
		return new MongoCustomConversions(List.of(new LocalDateToUtcDate(), new UtcDateToLocalDate(),
				new StringToPlanTier(), new PlanTierToString()));
	}

	@WritingConverter
	static final class LocalDateToUtcDate implements Converter<LocalDate, Date> {
		@Override
		public Date convert(LocalDate source) {
			return Date.from(source.atStartOfDay(ZoneOffset.UTC).toInstant());
		}
	}

	@ReadingConverter
	static final class UtcDateToLocalDate implements Converter<Date, LocalDate> {
		@Override
		public LocalDate convert(Date source) {
			return source.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
		}
	}

	@ReadingConverter
	static final class StringToPlanTier implements Converter<String, PlanTier> {
		@Override
		public PlanTier convert(String source) {
			return PlanTier.parseLenient(source);
		}
	}

	@WritingConverter
	static final class PlanTierToString implements Converter<PlanTier, String> {
		@Override
		public String convert(PlanTier source) {
			return source.name();
		}
	}
}
