package br.com.lhamalog.core.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SubscriptionPropertiesTest {

	@Test
	void defaultsDelinquencyGraceToFifteenDays() {
		SubscriptionProperties properties = new Binder(new MapConfigurationPropertySource())
				.bindOrCreate("lhamalog.subscription", Bindable.of(SubscriptionProperties.class));
		assertThat(properties.delinquencyGrace()).isEqualTo(Duration.ofDays(15));
	}

	@Test
	void honorsCustomDelinquencyGrace() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource();
		source.put("lhamalog.subscription.delinquency-grace", "7d");
		SubscriptionProperties properties = new Binder(source)
				.bind("lhamalog.subscription", Bindable.of(SubscriptionProperties.class))
				.get();
		assertThat(properties.delinquencyGrace()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void rejectsNonPositiveGrace() {
		assertThatThrownBy(() -> new SubscriptionProperties(Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SubscriptionProperties(Duration.ofDays(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
