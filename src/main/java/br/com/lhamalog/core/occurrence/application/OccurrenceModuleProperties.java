package br.com.lhamalog.core.occurrence.application;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Occurrence Module (spec 013, data-model "Configuração"). The secrets never appear in logs: only how
 * many there are and their length.
 *
 * @param internalAuth JWT of the internal routes {@code /internal/v1/**}
 * @param delivery analysis summary delivery
 */
@ConfigurationProperties("lhamalog.occurrence")
public record OccurrenceModuleProperties(InternalAuth internalAuth, Delivery delivery) {

	public OccurrenceModuleProperties {
		internalAuth = internalAuth != null ? internalAuth : new InternalAuth(null, null, null, null, null);
		delivery = delivery != null ? delivery : new Delivery(null, null, null, null);
	}

	/**
	 * @param secrets HS256 secrets, current first; up to two are kept for rotation
	 * @param issuer expected {@code iss}
	 * @param audience expected value inside {@code aud} of the module routes
	 * @param subjects services ({@code sub}) allowed to call the routes
	 * @param coreAudience expected value inside {@code aud} of {@code POST /internal/v1/alertas/finder} only
	 */
	public record InternalAuth(List<String> secrets, String issuer, String audience, List<String> subjects,
			String coreAudience) {

		public InternalAuth {
			secrets = secrets == null ? List.of()
					: secrets.stream().filter(secret -> secret != null && !secret.isBlank()).map(String::strip).toList();
			issuer = blankTo(issuer, "lhamalog-internal");
			audience = blankTo(audience, "lhamalog-modulo-ocorrencia");
			subjects = subjects == null || subjects.isEmpty() ? List.of("lhamalog-finder")
					: subjects.stream().filter(subject -> subject != null && !subject.isBlank()).map(String::strip).toList();
			coreAudience = blankTo(coreAudience, "lhamalog-core");
		}

	}

	/**
	 * @param lease how long a delivery in progress holds its key
	 * @param receiptRetention how long a confirmed receipt is kept
	 * @param maxFindings largest number of findings accepted in one summary
	 * @param maxSamples log samples kept per occurrence
	 */
	public record Delivery(Duration lease, Duration receiptRetention, Integer maxFindings, Integer maxSamples) {

		public Delivery {
			lease = lease != null ? lease : Duration.ofSeconds(30);
			receiptRetention = receiptRetention != null ? receiptRetention : Duration.ofDays(30);
			maxFindings = maxFindings != null && maxFindings > 0 ? maxFindings : 1000;
			maxSamples = maxSamples != null && maxSamples > 0 ? maxSamples : 20;
		}

	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}

}
