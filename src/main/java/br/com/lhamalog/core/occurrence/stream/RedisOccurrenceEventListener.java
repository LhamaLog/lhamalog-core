package br.com.lhamalog.core.occurrence.stream;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Receives the notices of every Core instance from Redis and hands them to the local connections (spec 015, R4). */
@Component
public class RedisOccurrenceEventListener implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(RedisOccurrenceEventListener.class);

	private final OccurrenceConnections connections;

	private final ObjectMapper mapper;

	public RedisOccurrenceEventListener(OccurrenceConnections connections, ObjectMapper mapper) {
		this.connections = connections;
		this.mapper = mapper;
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			JsonNode node = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
			connections.dispatch(new OccurrenceEvent(node.path("tenantId").asString(), node.path("productId").asString(),
					node.path("occurrenceId").asString(), node.path("occurrenceType").asString(),
					OccurrenceEvent.Change.valueOf(node.path("change").asString()), Instant.parse(node.path("at").asString())));
		}
		catch (RuntimeException invalid) {
			log.warn("event=occurrence.stream.invalid_notice | Notice from Redis ignored | error={}",
					invalid.getClass().getSimpleName());
		}
	}

}
