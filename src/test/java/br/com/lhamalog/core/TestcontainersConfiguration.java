package br.com.lhamalog.core;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Fixed image tags: Mongo and Redis match infra/local/compose.yaml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String LGTM_IMAGE = "grafana/otel-lgtm:0.37.0";

	static final String MONGO_IMAGE = "mongo:8.0.34";

	static final String REDIS_IMAGE = "redis:8.2.10";

	@Bean
	@ServiceConnection
	LgtmStackContainer grafanaLgtmContainer() {
		return new LgtmStackContainer(DockerImageName.parse(LGTM_IMAGE))
				.withStartupTimeout(Duration.ofMinutes(3));
	}

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse(MONGO_IMAGE));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
	}

}