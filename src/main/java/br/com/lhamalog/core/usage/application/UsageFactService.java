package br.com.lhamalog.core.usage.application;

import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsageFactService {
	public enum RecordResult { CREATED, DUPLICATE, CONFLICT }

	private final UsageFactRepository facts;
	private final PlanSubscriptionRepository subscriptions;
	private final InstantSource clock;

	@Autowired
	public UsageFactService(UsageFactRepository facts, PlanSubscriptionRepository subscriptions) {
		this(facts, subscriptions, InstantSource.system());
	}

	UsageFactService(UsageFactRepository facts, PlanSubscriptionRepository subscriptions, InstantSource clock) {
		this.facts = facts;
		this.subscriptions = subscriptions;
		this.clock = clock;
	}

	public RecordResult record(UsageFactRequest request) {
		return switch (facts.saveIfAbsent(new UsageFactRepository.StoredUsageFact(request, clock.instant()))) {
			case CREATED -> RecordResult.CREATED;
			case DUPLICATE -> RecordResult.DUPLICATE;
			case CONFLICT -> RecordResult.CONFLICT;
		};
	}

	public List<UsageFactRequest> find(String tenantId, String productId, Instant from, Instant to) {
		if (!from.isBefore(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time window");
		return facts.find(tenantId, productId, from, to).stream()
				.map(UsageFactRepository.StoredUsageFact::request).toList();
	}

	public UsageCycleSummary currentCycle(String tenantId, String productId) {
		PlanSubscription subscription = subscriptions.findByTenantIdAndProductId(tenantId, productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan subscription not found"));
		UsageCycle cycle = UsageCycle.calculate(subscription.planType(), subscription.firstPaymentDate(), clock.instant());
		List<UsageFactRequest> factsInCycle = find(tenantId, productId, cycle.from(), cycle.to());
		long count = factsInCycle.stream().mapToLong(UsageFactRequest::acceptedRecordCount).sum();
		long bytes = factsInCycle.stream().mapToLong(UsageFactRequest::acceptedBytes).sum();
		return new UsageCycleSummary(cycle.from(), cycle.to(), count, bytes, bytes / 1_000_000_000d);
	}
}
