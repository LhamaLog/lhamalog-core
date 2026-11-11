package br.com.lhamalog.core;

import br.com.lhamalog.core.usage.application.QuotaReservationProperties;
import br.com.lhamalog.core.usage.application.NotificationRedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({QuotaReservationProperties.class, NotificationRedisProperties.class})
@EnableScheduling
public class LhamalogCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(LhamalogCoreApplication.class, args);
	}

}
