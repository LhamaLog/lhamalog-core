package br.com.lhamalog.core.dashboard.application;

import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.CompanyMetrics;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.CompanyTotals;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductMetrics;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductSeries;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductSummary;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductTotals;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.Series;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.SeriesPoint;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ServiceRankingItem;
import br.com.lhamalog.core.dashboard.application.DashboardAggregateRepository.StoredAggregate;
import br.com.lhamalog.core.dashboard.application.HybridMetricsMerger.MergedLogMetrics;
import br.com.lhamalog.core.dashboard.application.UsageMetricsPort.UsageSnapshot;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardMetricsService {

	private static final Logger log = LoggerFactory.getLogger(DashboardMetricsService.class);
	private static final int MAX_FILTER_DAYS_CAP = 30;
	public static final String INVALID_FILTER = "invalid_filter";

	private final ProductService products;
	private final DashboardAggregateRepository aggregates;
	private final OpenObserveLogQueryPort openObserve;
	private final UsageMetricsPort usageMetrics;

	public DashboardMetricsService(ProductService products, DashboardAggregateRepository aggregates,
			OpenObserveLogQueryPort openObserve, UsageMetricsPort usageMetrics) {
		this.products = products;
		this.aggregates = aggregates;
		this.openObserve = openObserve;
		this.usageMetrics = usageMetrics;
	}

	public ProductMetrics productMetrics(CompanySession session, String productId, Instant from, Instant to,
			String severitiesRaw) {
		Product product = products.get(session, productId);
		int retentionDays = usageMetrics.retentionDays(session.tenantId());
		Set<LogSeverity> severities = parseSeverities(severitiesRaw);
		validateRange(from, to, retentionDays);
		Optional<Instant> watermark = aggregates.findWatermark(session.tenantId());
		MergedLogMetrics merged = loadMerged(session.tenantId(), product.id(), from, to, severities, watermark);
		UsageSnapshot usage = usageMetrics.forProduct(session.tenantId(), product.id(), from, to);
		log.info(
				"event=dashboard.product_metrics.ok | Product dashboard metrics served | tenantId={} productId={} from={} to={} logs={}",
				session.tenantId(), product.id(), from, to, merged.logs());
		return new ProductMetrics(product.id(), from, to, watermark.orElse(null), usage.retentionDays(),
				usage.volumeIngestaoBytes(),
				new ProductTotals(merged.logs(), merged.error(), merged.warning(), merged.info(), usage.traces(),
						usage.volumeUsedBytes()),
				new Series(merged.bucket(), toPoints(merged)), toRanking(merged));
	}

	public CompanyMetrics companyMetrics(CompanySession session, Instant from, Instant to, String productIdsRaw,
			String severitiesRaw) {
		int retentionDays = usageMetrics.retentionDays(session.tenantId());
		Set<LogSeverity> severities = parseSeverities(severitiesRaw);
		validateRange(from, to, retentionDays);
		List<Product> authorized = products.list(session);
		List<Product> selected = filterProducts(authorized, productIdsRaw, session.tenantId());
		if (selected.isEmpty()) {
			log.info(
					"event=dashboard.company_metrics.empty | Company dashboard metrics empty for session | tenantId={} userId={}",
					session.tenantId(), session.userId());
			return emptyCompany(from, to, retentionDays, aggregates.findWatermark(session.tenantId()).orElse(null));
		}
		Optional<Instant> watermark = aggregates.findWatermark(session.tenantId());
		long logs = 0;
		long error = 0;
		long warning = 0;
		List<ProductSeries> seriesByProduct = new ArrayList<>();
		List<ProductSummary> summaries = new ArrayList<>();
		Instant earliestWatermark = watermark.orElse(null);
		for (Product product : selected) {
			MergedLogMetrics merged = loadMerged(session.tenantId(), product.id(), from, to, severities, watermark);
			logs += merged.logs();
			error += merged.error();
			warning += merged.warning();
			seriesByProduct.add(new ProductSeries(product.id(), product.name(), merged.bucket(), toPoints(merged)));
			summaries.add(new ProductSummary(product.id(), product.name(), merged.logs(), merged.error(),
					merged.warning()));
		}
		log.info(
				"event=dashboard.company_metrics.ok | Company dashboard metrics served | tenantId={} products={} from={} to={} logs={}",
				session.tenantId(), selected.size(), from, to, logs);
		return new CompanyMetrics(from, to, earliestWatermark, retentionDays,
				new CompanyTotals(logs, error, warning, selected.size()), seriesByProduct, summaries);
	}

	MergedLogMetrics loadMerged(String tenantId, String productId, Instant from, Instant to,
			Set<LogSeverity> severities, Optional<Instant> watermark) {
		Instant consolidatedUntil = watermark.filter(mark -> mark.isAfter(from)).orElse(from);
		if (consolidatedUntil.isAfter(to)) {
			consolidatedUntil = to;
		}
		List<LogCountBucket> consolidated = List.of();
		if (from.isBefore(consolidatedUntil)) {
			consolidated = aggregates.findInRange(tenantId, productId, from, consolidatedUntil).stream()
					.map(DashboardMetricsService::toBucket)
					.toList();
		}
		List<LogCountBucket> open = List.of();
		if (consolidatedUntil.isBefore(to)) {
			open = openObserve.countLogs(tenantId, productId, consolidatedUntil, to, severities);
		}
		return HybridMetricsMerger.merge(consolidated, open, severities, from, to);
	}

	static Set<LogSeverity> parseSeverities(String raw) {
		if (raw == null || raw.isBlank()) {
			return EnumSet.allOf(LogSeverity.class);
		}
		Set<LogSeverity> parsed = EnumSet.noneOf(LogSeverity.class);
		for (String token : raw.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			LogSeverity severity = LogSeverity.parseFilter(trimmed).orElseThrow(() -> invalidFilter(
					"Severidade inválida: use ERROR, WARNING ou INFO."));
			parsed.add(severity);
		}
		if (parsed.isEmpty()) {
			throw invalidFilter("Informe ao menos uma severidade válida.");
		}
		return parsed;
	}

	static void validateRange(Instant from, Instant to, int retentionDays) {
		if (from == null || to == null) {
			throw invalidFilter("Os parâmetros from e to são obrigatórios.");
		}
		if (!to.isAfter(from)) {
			throw invalidFilter("O parâmetro to deve ser maior que from.");
		}
		int maxDays = Math.min(MAX_FILTER_DAYS_CAP, Math.max(retentionDays, 1));
		Duration max = Duration.ofDays(maxDays);
		if (Duration.between(from, to).compareTo(max) > 0) {
			throw invalidFilter("O intervalo excede o máximo permitido de " + maxDays + " dias.");
		}
	}

	private List<Product> filterProducts(List<Product> authorized, String productIdsRaw, String tenantId) {
		if (productIdsRaw == null || productIdsRaw.isBlank()) {
			return authorized;
		}
		Set<String> requested = new HashSet<>();
		for (String token : productIdsRaw.split(",")) {
			String id = token.trim();
			if (!id.isEmpty()) {
				requested.add(id);
			}
		}
		if (requested.isEmpty()) {
			return authorized;
		}
		Set<String> authorizedIds = new HashSet<>();
		for (Product product : authorized) {
			authorizedIds.add(product.id());
		}
		for (String id : requested) {
			if (!authorizedIds.contains(id)) {
				log.warn(
						"event=dashboard.company_metrics.invalid_product | productIds filter rejected | tenantId={} productId={}",
						tenantId, id);
				throw invalidFilter("Produto inacessível ou inválido no filtro productIds.");
			}
		}
		List<Product> selected = new ArrayList<>();
		for (Product product : authorized) {
			if (requested.contains(product.id())) {
				selected.add(product);
			}
		}
		return selected;
	}

	private static CompanyMetrics emptyCompany(Instant from, Instant to, int retentionDays,
			Instant dadosConsolidadosAte) {
		return new CompanyMetrics(from, to, dadosConsolidadosAte, retentionDays,
				new CompanyTotals(0, 0, 0, 0), List.of(), List.of());
	}

	private static LogCountBucket toBucket(StoredAggregate aggregate) {
		return new LogCountBucket(aggregate.hourStart(), aggregate.severity(), aggregate.serviceName(),
				aggregate.count());
	}

	private static List<SeriesPoint> toPoints(MergedLogMetrics merged) {
		return merged.points().stream()
				.map(point -> new SeriesPoint(point.start(), point.end(), point.error(), point.warning()))
				.toList();
	}

	private static List<ServiceRankingItem> toRanking(MergedLogMetrics merged) {
		return merged.serviceRanking().stream()
				.map(rank -> new ServiceRankingItem(rank.serviceName(), rank.error()))
				.toList();
	}

	private static ApiStatusException invalidFilter(String message) {
		return new ApiStatusException(HttpStatus.BAD_REQUEST, message, INVALID_FILTER);
	}
}
