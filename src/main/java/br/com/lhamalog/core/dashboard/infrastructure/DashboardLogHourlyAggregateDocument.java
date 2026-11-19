package br.com.lhamalog.core.dashboard.infrastructure;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("DashboardLogHourlyAggregate")
@CompoundIndexes({
		@CompoundIndex(name = "dashboard_agg_unique_bucket",
				def = "{'tenantId': 1, 'productId': 1, 'severity': 1, 'serviceName': 1, 'hourStart': 1}",
				unique = true),
		@CompoundIndex(name = "dashboard_agg_query_range",
				def = "{'tenantId': 1, 'productId': 1, 'hourStart': 1}")
})
public record DashboardLogHourlyAggregateDocument(
		@Id String id,
		String tenantId,
		String productId,
		String severity,
		String serviceName,
		Instant hourStart,
		long count,
		Instant consolidatedAt,
		int schemaVersion) {

	public static final int SCHEMA_VERSION = 1;

	public static String composeId(String tenantId, String productId, String severity, String serviceName,
			Instant hourStart) {
		return tenantId + "|" + productId + "|" + severity + "|" + serviceName + "|" + hourStart;
	}
}
