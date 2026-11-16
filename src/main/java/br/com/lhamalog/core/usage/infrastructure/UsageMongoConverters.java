package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.SubscriptionStatus;
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
 * <p>Also maps {@link SubscriptionStatus} so new documents write English names while legacy
 * Portuguese values remain readable.
 */
@Configuration
public class UsageMongoConverters {

	@Bean
	MongoCustomConversions mongoCustomConversions() {
		return utc();
	}

	public static MongoCustomConversions utc() {
		return new MongoCustomConversions(List.of(new LocalDateToUtcDate(), new UtcDateToLocalDate(),
				new StringToSubscriptionStatus(), new SubscriptionStatusToString()));
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
	static final class StringToSubscriptionStatus implements Converter<String, SubscriptionStatus> {
		@Override
		public SubscriptionStatus convert(String source) {
			return SubscriptionStatus.fromPersisted(source);
		}
	}

	@WritingConverter
	static final class SubscriptionStatusToString implements Converter<SubscriptionStatus, String> {
		@Override
		public String convert(SubscriptionStatus source) {
			return source.name();
		}
	}
}
