package br.com.lhamalog.core.usage.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("lhamalog.notifications.redis")
public record NotificationRedisProperties(@DefaultValue("false") boolean enabled,
		@DefaultValue("lhamalog:notifications:events") String stream) {
}
