package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.LowBalanceNotification;
import br.com.lhamalog.core.usage.application.LowBalanceNotificationPublisher;
import br.com.lhamalog.core.usage.application.NotificationRedisProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lhamalog.notifications.redis", name = "enabled", havingValue = "true")
public class RedisStreamsLowBalanceNotificationPublisher implements LowBalanceNotificationPublisher {
	private final StringRedisTemplate redis;
	private final NotificationRedisProperties properties;

	public RedisStreamsLowBalanceNotificationPublisher(StringRedisTemplate redis, NotificationRedisProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	@Override
	public void publish(LowBalanceNotification notification) {
		redis.opsForStream().add(properties.stream(), Map.of(
				"type", notification.type(),
				"severity", notification.severity(),
				"tenantId", notification.tenantId(),
				"productId", notification.productId(),
				"cycleStart", notification.cycleStart().toString(),
				"threshold", Integer.toString(notification.threshold()),
				"remainingBytes", Long.toString(notification.remainingBytes()),
				"mandatoryRoles", String.join(",", notification.mandatoryRoles()),
				"mandatoryChannels", String.join(",", notification.mandatoryChannels()),
				"idempotencyKey", notification.idempotencyKey()));
	}
}
