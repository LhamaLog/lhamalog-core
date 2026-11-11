package br.com.lhamalog.core.usage.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUsageFactRepository implements UsageFactRepository {
	private final Map<String, StoredUsageFact> facts = new ConcurrentHashMap<>();

	@Override
	public SaveResult saveIfAbsent(StoredUsageFact fact) {
		StoredUsageFact existing = facts.putIfAbsent(fact.request().usageFactId(), fact);
		if (existing == null) {
			return SaveResult.CREATED;
		}
		return existing.semanticallyEquals(fact) ? SaveResult.DUPLICATE : SaveResult.CONFLICT;
	}

	@Override
	public List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to) {
		return matching(tenantId, productId, from, to);
	}

	@Override
	public Page findPage(String tenantId, String productId, Instant from, Instant to, int limit, String cursor) {
		List<StoredUsageFact> matching = matching(tenantId, productId, from, to);
		int start = 0;
		if (cursor != null && !cursor.isBlank()) {
			UsageFactCursor decoded = UsageFactCursor.decode(cursor);
			start = 0;
			for (int index = 0; index < matching.size(); index++) {
				StoredUsageFact fact = matching.get(index);
				Instant acceptedAt = fact.request().acceptedAt().toInstant();
				String id = fact.request().usageFactId();
				boolean after = acceptedAt.isAfter(decoded.acceptedAt())
						|| (acceptedAt.equals(decoded.acceptedAt()) && id.compareTo(decoded.id()) > 0);
				if (after) {
					start = index;
					break;
				}
				start = matching.size();
			}
		}
		int end = Math.min(matching.size(), start + limit);
		List<StoredUsageFact> items = matching.subList(start, end);
		String nextCursor = null;
		if (end < matching.size() && !items.isEmpty()) {
			StoredUsageFact last = items.get(items.size() - 1);
			nextCursor = new UsageFactCursor(last.request().acceptedAt().toInstant(),
					last.request().usageFactId()).encode();
		}
		return new Page(List.copyOf(items), nextCursor);
	}

	public List<StoredUsageFact> findAll() {
		return new ArrayList<>(facts.values());
	}

	private List<StoredUsageFact> matching(String tenantId, String productId, Instant from, Instant to) {
		return facts.values().stream()
				.filter(fact -> fact.request().tenantId().equals(tenantId)
						&& fact.request().productId().equals(productId))
				.filter(fact -> {
					Instant acceptedAt = fact.request().acceptedAt().toInstant();
					return !acceptedAt.isBefore(from) && acceptedAt.isBefore(to);
				})
				.sorted(Comparator.comparing((StoredUsageFact fact) -> fact.request().acceptedAt())
						.thenComparing(fact -> fact.request().usageFactId()))
				.toList();
	}
}
