package br.com.lhamalog.core.dashboard.infrastructure;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("DashboardAggregationWatermark")
public record DashboardAggregationWatermarkDocument(
		@Id String tenantId,
		Instant dadosConsolidadosAte,
		Instant updatedAt) {
}
