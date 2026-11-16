package br.com.lhamalog.core.usage.application;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactPageResponse;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import br.com.lhamalog.core.usage.infrastructure.UsageFactRepository;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsageFactService {
	public enum RecordResult { CREATED, DUPLICATE, CONFLICT }

	public static final int DEFAULT_PAGE_LIMIT = 50;
	public static final int MAX_PAGE_LIMIT = 100;

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

	public UsageFactPageResponse find(String tenantId, String productId, Instant from, Instant to,
			Integer limit, String cursor) {
		validateWindow(from, to);
		int pageSize = resolveLimit(limit);
		try {
			UsageFactRepository.Page page = facts.findPage(tenantId, productId, from, to, pageSize, cursor);
			return new UsageFactPageResponse(page.items().stream()
					.map(UsageFactRepository.StoredUsageFact::request).toList(), page.nextCursor());
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor", exception);
		}
	}

	public UsageCycleSummary currentCycle(String tenantId, String productId) {
		PlanSubscription subscription = subscriptions.findByTenantIdAndProductId(tenantId, productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan subscription not found"));
		return UsageCycle.calculate(subscription.planType(), subscription.firstPaymentDate(), clock.instant())
				.map(cycle -> summarize(tenantId, productId, cycle))
				.orElseGet(UsageCycleSummary::inactive);
	}

	private UsageCycleSummary summarize(String tenantId, String productId, UsageCycle cycle) {
		List<UsageFactRequest> factsInCycle = facts.find(tenantId, productId, cycle.from(), cycle.to()).stream()
				.map(UsageFactRepository.StoredUsageFact::request).toList();
		return UsageCycleSummary.active(cycle.from(), cycle.to(),
				domainSummary(factsInCycle, UsageDomain.LOGS),
				domainSummary(factsInCycle, UsageDomain.TRACES));
	}

	private static DomainUsageSummary domainSummary(List<UsageFactRequest> factsInCycle, UsageDomain domain) {
		long count = factsInCycle.stream().filter(fact -> fact.domain() == domain)
				.mapToLong(UsageFactRequest::acceptedRecordCount).sum();
		long bytes = factsInCycle.stream().filter(fact -> fact.domain() == domain)
				.mapToLong(UsageFactRequest::acceptedBytes).sum();
		return DomainUsageSummary.of(count, bytes);
	}

	static void validateWindow(Instant from, Instant to) {
		if (from == null || to == null || !from.isBefore(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time window");
		}
	}

	static int resolveLimit(Integer limit) {
		if (limit == null || limit < 1) {
			return DEFAULT_PAGE_LIMIT;
		}
		return Math.min(limit, MAX_PAGE_LIMIT);
	}
}
