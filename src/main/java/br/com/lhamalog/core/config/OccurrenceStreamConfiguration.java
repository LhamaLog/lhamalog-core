package br.com.lhamalog.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import br.com.lhamalog.core.occurrence.stream.OccurrenceStreamHandler;
import br.com.lhamalog.core.occurrence.stream.OccurrenceStreamProperties;
import br.com.lhamalog.core.occurrence.stream.RedisOccurrenceEventListener;

/**
 * Occurrence WebSocket (spec 015): {@code /ws/occurrences} only for the Web origins (the Clerk authorized parties, as
 * in CORS), and the Redis channel that carries the notices between Core instances.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableConfigurationProperties(OccurrenceStreamProperties.class)
public class OccurrenceStreamConfiguration implements WebSocketConfigurer {

	static final String PATH = "/ws/occurrences";

	private final OccurrenceStreamHandler handler;

	private final ClerkProperties clerk;

	public OccurrenceStreamConfiguration(OccurrenceStreamHandler handler, ClerkProperties clerk) {
		this.handler = handler;
		this.clerk = clerk;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, PATH).setAllowedOrigins(clerk.authorizedParties().toArray(String[]::new));
	}

	@Bean
	RedisMessageListenerContainer occurrenceEventsListenerContainer(RedisConnectionFactory connections,
			RedisOccurrenceEventListener listener, OccurrenceStreamProperties settings) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connections);
		container.addMessageListener(listener, new ChannelTopic(settings.channel()));
		return container;
	}

}
