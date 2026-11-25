package br.com.lhamalog.core.occurrence.stream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.occurrence.application.OccurrenceEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes the notices on the Redis channel shared by the Core instances (spec 015, research R4), off the delivery
 * thread: one worker and a bounded queue, so the Finder never waits on Redis (SC-004). A lost notice is acceptable:
 * the screen synchronizes when it reconnects.
 */
@Component
public class RedisOccurrenceEventPublisher implements OccurrenceEventPublisher, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(RedisOccurrenceEventPublisher.class);

	private final StringRedisTemplate redis;

	private final ObjectMapper mapper;

	private final String channel;

	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(1_000), (runnable) -> {
				Thread thread = new Thread(runnable, "occurrence-events");
				thread.setDaemon(true);
				return thread;
			});

	public RedisOccurrenceEventPublisher(StringRedisTemplate redis, ObjectMapper mapper,
			OccurrenceStreamProperties settings) {
		this.redis = redis;
		this.mapper = mapper;
		this.channel = settings.channel();
	}

	@Override
	public void publish(List<OccurrenceEvent> events) {
		List<OccurrenceEvent> copy = List.copyOf(events);
		try {
			worker.execute(() -> send(copy));
		}
		catch (RejectedExecutionException full) {
			log.warn("event=occurrence.stream.publish_failed | Notice queue full; notices dropped | notices={}", copy.size());
		}
	}

	private void send(List<OccurrenceEvent> events) {
		for (OccurrenceEvent event : events) {
			try {
				redis.convertAndSend(channel, mapper.writeValueAsString(toMessage(event)));
			}
			catch (RuntimeException failure) {
				log.warn("event=occurrence.stream.publish_failed | Notice not published to Redis | tenantId={} error={}",
						event.tenantId(), failure.getClass().getSimpleName());
			}
		}
	}

	static Map<String, Object> toMessage(OccurrenceEvent event) {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("tenantId", event.tenantId());
		message.put("productId", event.productId());
		message.put("occurrenceId", event.occurrenceId());
		message.put("occurrenceType", event.occurrenceType());
		message.put("change", event.change().name());
		message.put("at", String.valueOf(event.at()));
		return message;
	}

	@Override
	public void destroy() {
		worker.shutdown();
	}

}
