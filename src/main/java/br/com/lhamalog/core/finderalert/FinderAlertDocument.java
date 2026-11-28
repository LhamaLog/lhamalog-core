package br.com.lhamalog.core.finderalert;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One durable Finder alert. Distinct from {@code Ocorrencia} and from the delivery receipt. {@code aceito} is not
 * stored: it exists only on the HTTP response.
 */
@Document(FinderAlertDocument.COLLECTION)
@CompoundIndex(name = "finder_alert_unique_key",
		def = "{'tenantId': 1, 'productId': 1, 'batchId': 1, 'motivo': 1}", unique = true)
public record FinderAlertDocument(@Id String id, String tenantId, String productId, String batchId, String motivo) {

	public static final String COLLECTION = "finder_alerts";

}
