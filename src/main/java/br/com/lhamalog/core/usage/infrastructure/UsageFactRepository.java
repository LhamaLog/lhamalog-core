package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.api.UsageFactRequest;
import java.time.Instant;
import java.util.List;

public interface UsageFactRepository {

	enum SaveResult { CREATED, DUPLICATE, CONFLICT }

	SaveResult saveIfAbsent(StoredUsageFact fact);

	List<StoredUsageFact> find(String tenantId, String productId, Instant from, Instant to);

	Page findPage(String tenantId, String productId, Instant from, Instant to, int limit, String cursor);

	record StoredUsageFact(UsageFactRequest request, Instant receivedAt) {
		public boolean semanticallyEquals(StoredUsageFact other) {
			return request.equals(other.request);
		}
	}

	record Page(List<StoredUsageFact> items, String nextCursor) {}
}
