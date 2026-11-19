package br.com.lhamalog.core.dashboard.application;

import br.com.lhamalog.core.dashboard.application.DashboardAggregateRepository.ProductScope;
import br.com.lhamalog.core.logging.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSID_011: consolidates OpenObserve log counts into hourly Mongo aggregates and advances
 * {@code dadosConsolidadosAte}. Reads logs now; Occurrences come after LLT-104.
 */
@Component
@ConditionalOnProperty(name = "lhamalog.dashboard.aggregation.enabled", havingValue = "true", matchIfMissing = true)
public class DashboardAggregationJob {

	private static final Logger log = LoggerFactory.getLogger(DashboardAggregationJob.class);

	private final DashboardAggregateRepository aggregates;
	private final OpenObserveLogQueryPort openObserve;
	private final Clock clock;

	@Autowired
	public DashboardAggregationJob(DashboardAggregateRepository aggregates, OpenObserveLogQueryPort openObserve) {
		this(aggregates, openObserve, Clock.systemUTC());
	}

	DashboardAggregationJob(DashboardAggregateRepository aggregates, OpenObserveLogQueryPort openObserve, Clock clock) {
		this.aggregates = aggregates;
		this.openObserve = openObserve;
		this.clock = clock;
	}

	@Scheduled(cron = "${lhamalog.dashboard.aggregation.cron:0 0 * * * *}")
	public void run() {
		String cid = CorrelationId.newId();
		MDC.put(CorrelationId.KEY, cid);
		try {
			Instant now = clock.instant();
			Instant currentHour = HybridMetricsMerger.truncateHour(now);
			List<ProductScope> scopes = aggregates.listActiveProductScopes();
			log.info("event=dashboard.aggregation.started | SSID_011 aggregation started | scopes={} hour={}",
					scopes.size(), currentHour);
			Map<String, Instant> proposedWatermarks = new HashMap<>();
			Set<String> failedTenants = new HashSet<>();
			int upserts = 0;
			for (ProductScope scope : scopes) {
				if (failedTenants.contains(scope.tenantId())) {
					continue;
				}
				try {
					upserts += consolidateProduct(scope, currentHour, now, proposedWatermarks);
				}
				catch (RuntimeException exception) {
					log.error(
							"event=dashboard.aggregation.product_failed | Aggregation failed for product; watermark not advanced for tenant | tenantId={} productId={}",
							scope.tenantId(), scope.productId());
					failedTenants.add(scope.tenantId());
					proposedWatermarks.remove(scope.tenantId());
				}
			}
			int advanced = 0;
			for (Map.Entry<String, Instant> entry : proposedWatermarks.entrySet()) {
				if (failedTenants.contains(entry.getKey())) {
					continue;
				}
				aggregates.saveWatermark(entry.getKey(), entry.getValue(), now);
				advanced++;
			}
			log.info(
					"event=dashboard.aggregation.completed | SSID_011 aggregation completed | scopes={} upserts={} watermarks={}",
					scopes.size(), upserts, advanced);
		}
		finally {
			MDC.remove(CorrelationId.KEY);
		}
	}

	private int consolidateProduct(ProductScope scope, Instant currentHour, Instant now,
			Map<String, Instant> proposedWatermarks) {
		Optional<Instant> existing = aggregates.findWatermark(scope.tenantId());
		Instant from = existing.orElse(currentHour.minus(1, ChronoUnit.HOURS));
		if (!from.isBefore(currentHour)) {
			return 0;
		}
		List<LogCountBucket> buckets = openObserve.countLogs(scope.tenantId(), scope.productId(), from, currentHour,
				EnumSet.allOf(LogSeverity.class));
		int written = 0;
		for (LogCountBucket bucket : buckets) {
			if (!bucket.hourStart().isBefore(currentHour)) {
				continue;
			}
			aggregates.upsert(bucket, scope.tenantId(), scope.productId(), now);
			written++;
		}
		proposedWatermarks.putIfAbsent(scope.tenantId(), currentHour);
		return written;
	}
}
