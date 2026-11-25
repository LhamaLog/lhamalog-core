package br.com.lhamalog.core.occurrence.stream;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the occurrence WebSocket (spec 015).
 *
 * @param channel Redis pub/sub channel shared by the Core instances
 * @param authTimeout time a new connection has to send its {@code auth} message
 * @param expiryGrace time a connection lives after its token expired without a renewal
 * @param productsRefresh how often the visible products of a connection are read again
 */
@ConfigurationProperties("lhamalog.occurrence.stream")
public record OccurrenceStreamProperties(String channel, Duration authTimeout, Duration expiryGrace,
		Duration productsRefresh) {

	public OccurrenceStreamProperties {
		channel = channel == null || channel.isBlank() ? "lhamalog:occurrence-events" : channel.strip();
		authTimeout = authTimeout != null ? authTimeout : Duration.ofSeconds(10);
		expiryGrace = expiryGrace != null ? expiryGrace : Duration.ofSeconds(30);
		productsRefresh = productsRefresh != null ? productsRefresh : Duration.ofSeconds(60);
	}

}
