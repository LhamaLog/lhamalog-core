package br.com.lhamalog.core.dashboard.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges consolidated Mongo aggregates with open-interval OpenObserve counts and builds
 * series / ranking views. {@code occurrences} is never produced.
 */
public final class HybridMetricsMerger {

	public static final Duration HOUR_BUCKET_MAX = Duration.ofHours(48);
	public static final int SERVICE_RANKING_LIMIT = 5;

	private HybridMetricsMerger() {
	}

	public static String bucketFor(Instant from, Instant to) {
		return Duration.between(from, to).compareTo(HOUR_BUCKET_MAX) <= 0 ? "hour" : "day";
	}

	public static Instant truncateHour(Instant instant) {
		return instant.truncatedTo(ChronoUnit.HOURS);
	}

	public static Instant truncateDay(Instant instant) {
		return instant.truncatedTo(ChronoUnit.DAYS);
	}

	public static MergedLogMetrics merge(List<LogCountBucket> consolidated, List<LogCountBucket> openInterval,
			Set<LogSeverity> severities, Instant from, Instant to) {
		Set<LogSeverity> filter = severities == null || severities.isEmpty()
				? EnumSet.allOf(LogSeverity.class)
				: EnumSet.copyOf(severities);
		Map<Key, Long> cells = new HashMap<>();
		addAll(cells, consolidated, filter, from, to);
		addAll(cells, openInterval, filter, from, to);

		EnumMap<LogSeverity, Long> totals = new EnumMap<>(LogSeverity.class);
		for (LogSeverity severity : LogSeverity.values()) {
			totals.put(severity, 0L);
		}
		Map<String, Long> errorsByService = new HashMap<>();
		Map<Instant, EnumMap<LogSeverity, Long>> byBucketStart = new LinkedHashMap<>();
		String bucket = bucketFor(from, to);

		for (Map.Entry<Key, Long> entry : cells.entrySet()) {
			Key key = entry.getKey();
			long count = entry.getValue();
			totals.merge(key.severity(), count, Long::sum);
			if (key.severity() == LogSeverity.ERROR) {
				errorsByService.merge(key.serviceName(), count, Long::sum);
			}
			Instant start = "hour".equals(bucket) ? key.hourStart() : truncateDay(key.hourStart());
			byBucketStart.computeIfAbsent(start, ignored -> {
				EnumMap<LogSeverity, Long> map = new EnumMap<>(LogSeverity.class);
				for (LogSeverity severity : LogSeverity.values()) {
					map.put(severity, 0L);
				}
				return map;
			}).merge(key.severity(), count, Long::sum);
		}

		long logs = totals.values().stream().mapToLong(Long::longValue).sum();
		List<SeriesPoint> points = new ArrayList<>();
		List<Instant> starts = byBucketStart.keySet().stream().sorted().toList();
		for (Instant start : starts) {
			EnumMap<LogSeverity, Long> counts = byBucketStart.get(start);
			Instant end = "hour".equals(bucket) ? start.plus(1, ChronoUnit.HOURS) : start.plus(1, ChronoUnit.DAYS);
			points.add(new SeriesPoint(start, end, counts.getOrDefault(LogSeverity.ERROR, 0L),
					counts.getOrDefault(LogSeverity.WARNING, 0L)));
		}

		List<ServiceRank> ranking = errorsByService.entrySet().stream()
				.sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
						.thenComparing(Map.Entry::getKey))
				.limit(SERVICE_RANKING_LIMIT)
				.map(entry -> new ServiceRank(entry.getKey(), entry.getValue()))
				.toList();

		return new MergedLogMetrics(logs, totals.getOrDefault(LogSeverity.ERROR, 0L),
				totals.getOrDefault(LogSeverity.WARNING, 0L), totals.getOrDefault(LogSeverity.INFO, 0L), bucket,
				points, ranking);
	}

	private static void addAll(Map<Key, Long> cells, List<LogCountBucket> buckets, Set<LogSeverity> filter,
			Instant from, Instant to) {
		if (buckets == null) {
			return;
		}
		for (LogCountBucket bucket : buckets) {
			if (!filter.contains(bucket.severity())) {
				continue;
			}
			Instant hour = bucket.hourStart();
			if (hour.isBefore(from) || !hour.isBefore(to)) {
				continue;
			}
			cells.merge(new Key(hour, bucket.severity(), bucket.serviceName()), bucket.count(), Long::sum);
		}
	}

	private record Key(Instant hourStart, LogSeverity severity, String serviceName) {
	}

	public record SeriesPoint(Instant start, Instant end, long error, long warning) {
	}

	public record ServiceRank(String serviceName, long error) {
	}

	public record MergedLogMetrics(long logs, long error, long warning, long info, String bucket,
			List<SeriesPoint> points, List<ServiceRank> serviceRanking) {
	}
}
