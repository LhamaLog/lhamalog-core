package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.api.UsageFactRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface UsageFactRepository {

	enum SaveResult { CREATED, DUPLICATE, CONFLICT }

	SaveResult saveIfAbsent(StoredUsageFact fact);

	List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to);

	record StoredUsageFact(UsageFactRequest request, Instant receivedAt) {
		public boolean semanticallyEquals(StoredUsageFact other) {
			return request.equals(other.request);
		}
	}

	final class InMemory implements UsageFactRepository {
		private final Map<String, StoredUsageFact> facts = new ConcurrentHashMap<>();

		@Override
		public SaveResult saveIfAbsent(StoredUsageFact fact) {
			StoredUsageFact existing = facts.putIfAbsent(fact.request().usageFactId(), fact);
			if (existing == null) return SaveResult.CREATED;
			return existing.semanticallyEquals(fact) ? SaveResult.DUPLICATE : SaveResult.CONFLICT;
		}

		@Override
		public List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to) {
			return facts.values().stream()
					.filter(fact -> fact.request().tenantId().equals(tenantId)
							&& fact.request().productId().equals(productId))
					.filter(fact -> {
						Instant acceptedAt = fact.request().acceptedAt().toInstant();
						return !acceptedAt.isBefore(from) && acceptedAt.isBefore(to);
					})
					.sorted(Comparator.comparing(fact -> fact.request().acceptedAt()))
					.toList();
		}

		public List<StoredUsageFact> findAll() {
			return new ArrayList<>(facts.values());
		}
	}
}
