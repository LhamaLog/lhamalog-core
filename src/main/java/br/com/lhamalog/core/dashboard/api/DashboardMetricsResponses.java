package br.com.lhamalog.core.dashboard.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** JSON shapes for dashboard metrics (contract LLT-159). No impact seals; no {@code occurrences}. */
public final class DashboardMetricsResponses {

	private DashboardMetricsResponses() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ProductMetrics(
			String productId,
			Instant from,
			Instant to,
			Instant dadosConsolidadosAte,
			int retentionDays,
			Long volumeIngestaoBytes,
			ProductTotals totals,
			Series series,
			List<ServiceRankingItem> serviceRanking) {
	}

	public record ProductTotals(
			long logs,
			long error,
			long warning,
			long info,
			long traces,
			long volumeUsedBytes) {
	}

	public record Series(String bucket, List<SeriesPoint> points) {
	}

	/** {@code occurrences} intentionally absent in this MVP. */
	public record SeriesPoint(Instant start, Instant end, long error, long warning) {
	}

	public record ServiceRankingItem(String serviceName, long error) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CompanyMetrics(
			Instant from,
			Instant to,
			Instant dadosConsolidadosAte,
			int retentionDays,
			CompanyTotals totals,
			List<ProductSeries> seriesByProduct,
			List<ProductSummary> products) {
	}

	public record CompanyTotals(long logs, long error, long warning, int productsMonitored) {
	}

	public record ProductSeries(String productId, String productName, String bucket, List<SeriesPoint> points) {
	}

	public record ProductSummary(String productId, String productName, long logs, long error, long warning) {
	}
}
