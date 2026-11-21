package br.com.lhamalog.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.upload.CollectorManualUploadClient;
import br.com.lhamalog.core.upload.RestCollectorManualUploadClient;

/** Wires the manual upload client. The service token is read only from the environment. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ManualUploadProperties.class)
public class ManualUploadConfiguration {

	@Bean
	CollectorManualUploadClient collectorManualUploadClient(ManualUploadProperties properties,
			@Value("${LHAMALOG_CORE_COLLECTOR_TOKEN:}") String serviceToken) {
		// A dedicated RestClient.builder() is used on purpose: the auto-configured Boot
		// builder can later gain a RestClientCustomizer / ClientHttpRequestInterceptor,
		// and InterceptingClientHttpRequest would buffer the whole multipart body (up to 100 MB).
		return new RestCollectorManualUploadClient(RestClient.builder(), properties, serviceToken);
	}

	@Component
	static class StartupWarnings {

		private static final Logger log = LoggerFactory.getLogger(StartupWarnings.class);

		private final ManualUploadProperties properties;

		private final Environment environment;

		private final String serviceToken;

		private final int serverPort;

		StartupWarnings(ManualUploadProperties properties, Environment environment,
				@Value("${LHAMALOG_CORE_COLLECTOR_TOKEN:}") String serviceToken,
				@Value("${server.port:8080}") int serverPort) {
			this.properties = properties;
			this.environment = environment;
			this.serviceToken = serviceToken;
			this.serverPort = serverPort;
		}

		@EventListener(ApplicationReadyEvent.class)
		void warnOnMisconfiguration() {
			boolean local = environment.acceptsProfiles(Profiles.of("local"));
			for (String warning : ManualUploadStartupChecks.warnings(local, serviceToken, properties.collectorUrl(),
					serverPort)) {
				log.warn("{}", warning);
			}
		}
	}
}
