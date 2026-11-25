package br.com.lhamalog.core.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import br.com.lhamalog.core.billing.application.GatewayEventProcessor.Result;
import br.com.lhamalog.core.billing.application.port.out.GatewayEventRepository;
import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.application.port.out.TenantSubscriptionRepository;
import br.com.lhamalog.core.billing.domain.GatewayEvent;
import br.com.lhamalog.core.billing.domain.GatewayEventStatus;
import br.com.lhamalog.core.billing.domain.GatewayNotification;
import br.com.lhamalog.core.billing.domain.OrderStatus;
import br.com.lhamalog.core.billing.domain.Plan;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.billing.domain.TenantSubscription;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.member.domain.Member;
import br.com.lhamalog.core.member.domain.MemberRole;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning;
import br.com.lhamalog.core.usage.application.PlanTier;

/** Spec 010, US3: failure in the middle of the activation and resume on resend (research R4/R5). */
class SubscriptionActivationServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T13:00:00Z"), ZoneOffset.UTC);

	private final Orders orders = new Orders();

	private final Tenants tenants = new Tenants();

	private final Subscriptions subscriptions = new Subscriptions();

	private final Members members = new Members();

	private final Events events = new Events();

	private final SubscriptionActivationService activation = new SubscriptionActivationService(orders, tenants,
			subscriptions, members, CLOCK, () -> UUID.randomUUID().toString());

	private final GatewayEventProcessor processor = new GatewayEventProcessor(events, activation, CLOCK);

	@Test
	void failureAfterTheTenantIsCreatedIsResumedByTheResend() {
		SubscriptionOrder order = order("org_a", "user_a");
		subscriptions.failNext.set(1);
		GatewayNotification confirmation = confirmation("evt_1", order.orderId());

		assertThatThrownBy(() -> processor.process(confirmation)).isInstanceOf(IllegalStateException.class);
		assertThat(events.byId.get("evt_1").status()).isEqualTo(GatewayEventStatus.FAILED);
		assertThat(events.byId.get("evt_1").reason()).isEqualTo("IllegalStateException");
		assertThat(tenants.byOrg).hasSize(1);
		assertThat(orders.byId.get(order.orderId()).status()).isEqualTo(OrderStatus.PENDING);

		assertThat(processor.process(confirmation)).isEqualTo(Result.PROCESSED);
		assertThat(events.byId.get("evt_1").status()).isEqualTo(GatewayEventStatus.EXECUTED);
		assertThat(events.byId.get("evt_1").attempts()).isEqualTo(2);
		assertThat(tenants.byOrg).hasSize(1);
		String tenantId = tenants.byOrg.get("org_a").tenantId();
		assertThat(subscriptions.byTenant).containsOnlyKeys(tenantId);
		assertThat(members.all).singleElement().satisfies((member) -> assertThat(member.role()).isEqualTo(MemberRole.DONO));
		assertThat(orders.byId.get(order.orderId()).status()).isEqualTo(OrderStatus.PAID);

		assertThat(processor.process(confirmation)).isEqualTo(Result.DUPLICATE);
	}

	@Test
	void existingMemberIsPromotedToOwner() {
		SubscriptionOrder order = order("org_b", "user_b");
		members.beforeLookup = (tenantId) -> members.all
			.add(new Member("m-clerk", tenantId, "user_b", "orgmem_1", MemberRole.ADMIN, "Nome", null, 10, 0, null, null));
		processor.process(confirmation("evt_2", order.orderId()));
		assertThat(members.all).singleElement().satisfies((member) -> {
			assertThat(member.memberId()).isEqualTo("m-clerk");
			assertThat(member.role()).isEqualTo(MemberRole.DONO);
		});
	}

	@Test
	void eventBeingProcessedIsReportedAsInProgress() {
		events.byId.put("evt_3", new GatewayEvent("evt_3", "ASAAS", "PAYMENT_RECEIVED", "o", null,
				GatewayEventStatus.RECEIVED, null, null, 1, CLOCK.instant(), null));
		assertThat(processor.process(confirmation("evt_3", "o"))).isEqualTo(Result.IN_PROGRESS);
	}

	@Test
	void eventLeftReceivedByAStoppedDeliveryIsTakenAfterTheLease() {
		SubscriptionOrder order = order("org_c", "user_c");
		Instant abandonedAt = CLOCK.instant().minus(GatewayEventProcessor.DEFAULT_LEASE).minusSeconds(1);
		events.byId.put("evt_4", new GatewayEvent("evt_4", "ASAAS", "PAYMENT_RECEIVED", order.orderId(), null,
				GatewayEventStatus.RECEIVED, null, null, 1, abandonedAt, null));

		assertThat(processor.process(confirmation("evt_4", order.orderId()))).isEqualTo(Result.PROCESSED);
		assertThat(events.byId.get("evt_4").status()).isEqualTo(GatewayEventStatus.EXECUTED);
		assertThat(events.byId.get("evt_4").attempts()).isEqualTo(2);
		assertThat(orders.byId.get(order.orderId()).status()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void failureToRecordTheFailureKeepsTheOriginalErrorAndTheLeaseFreesTheEvent() {
		SubscriptionOrder order = order("org_d", "user_d");
		subscriptions.failNext.set(1);
		events.failNextFailedRecord = true;

		assertThatThrownBy(() -> processor.process(confirmation("evt_5", order.orderId())))
			.isInstanceOf(IllegalStateException.class)
			.satisfies((ex) -> assertThat(ex.getSuppressed()).hasSize(1));
		assertThat(events.byId.get("evt_5").status()).isEqualTo(GatewayEventStatus.RECEIVED);
		assertThat(processor.process(confirmation("evt_5", order.orderId()))).isEqualTo(Result.IN_PROGRESS);

		GatewayEventProcessor later = new GatewayEventProcessor(events, activation,
				Clock.offset(CLOCK, GatewayEventProcessor.DEFAULT_LEASE.plusSeconds(1)));
		assertThat(later.process(confirmation("evt_5", order.orderId()))).isEqualTo(Result.PROCESSED);
		assertThat(events.byId.get("evt_5").status()).isEqualTo(GatewayEventStatus.EXECUTED);
	}

	private SubscriptionOrder order(String org, String user) {
		SubscriptionOrder order = SubscriptionOrder.pending(UUID.randomUUID().toString(), org, user, "Empresa",
				new Plan(PlanTier.STARTER, "Starter", new BigDecimal("49.90")), CLOCK.instant());
		orders.insert(order);
		return order;
	}

	private static GatewayNotification confirmation(String eventId, String orderId) {
		return new GatewayNotification(eventId, "ASAAS", "PAYMENT_RECEIVED", orderId, "pay_1", new BigDecimal("49.9"));
	}

	static final class Orders implements SubscriptionOrderRepository {

		final Map<String, SubscriptionOrder> byId = new ConcurrentHashMap<>();

		@Override
		public void insert(SubscriptionOrder order) {
			byId.put(order.orderId(), order);
		}

		@Override
		public void cancelPending(String clerkOrgId, Instant now) {
		}

		@Override
		public Optional<SubscriptionOrder> findById(String orderId) {
			return Optional.ofNullable(byId.get(orderId));
		}

		@Override
		public Optional<SubscriptionOrder> findLatestByClerkOrgId(String clerkOrgId) {
			return Optional.empty();
		}

		@Override
		public Optional<SubscriptionOrder> findByGatewayChargeId(String gatewayChargeId) {
			return Optional.empty();
		}

		@Override
		public void markPaid(String orderId, Instant now) {
			SubscriptionOrder o = byId.get(orderId);
			byId.put(orderId, new SubscriptionOrder(o.orderId(), o.clerkOrgId(), o.clerkUserId(), o.companyName(),
					o.planTier(), o.price(), OrderStatus.PAID, o.gatewayChargeId(), o.paymentUrl(), o.createdAt(), now,
					o.canceledAt()));
		}

	}

	static final class Tenants implements TenantProvisioning {

		final Map<String, ProvisionedTenant> byOrg = new ConcurrentHashMap<>();

		@Override
		public ProvisionedTenant provisionForOrder(String clerkOrgId, String name, String orderId) {
			ProvisionedTenant existing = byOrg.get(clerkOrgId);
			if (existing != null) {
				return new ProvisionedTenant(existing.tenantId(), existing.originOrderId(), false);
			}
			ProvisionedTenant created = new ProvisionedTenant("tenant-" + clerkOrgId, orderId, true);
			byOrg.put(clerkOrgId, created);
			return created;
		}

	}

	static final class Subscriptions implements TenantSubscriptionRepository {

		final Map<String, TenantSubscription> byTenant = new ConcurrentHashMap<>();

		final AtomicInteger failNext = new AtomicInteger();

		@Override
		public void createIfAbsent(TenantSubscription subscription) {
			if (failNext.getAndUpdate((n) -> Math.max(0, n - 1)) > 0) {
				throw new IllegalStateException("Simulated database failure");
			}
			byTenant.putIfAbsent(subscription.tenantId(), subscription);
		}

		@Override
		public Optional<TenantSubscription> findByTenantId(String tenantId) {
			return Optional.ofNullable(byTenant.get(tenantId));
		}

	}

	static final class Members implements MemberRepository {

		final List<Member> all = new ArrayList<>();

		java.util.function.Consumer<String> beforeLookup = (tenantId) -> {
		};

		@Override
		public Optional<Member> findByTenantIdAndClerkUserId(String tenantId, String clerkUserId) {
			beforeLookup.accept(tenantId);
			beforeLookup = (ignored) -> {
			};
			return all.stream()
				.filter((m) -> m.tenantId().equals(tenantId) && m.clerkUserId().equals(clerkUserId))
				.findFirst();
		}

		@Override
		public List<Member> findByClerkUserId(String clerkUserId) {
			return all.stream().filter((m) -> m.clerkUserId().equals(clerkUserId)).toList();
		}

		@Override
		public void insert(Member member) {
			all.add(member);
		}

		@Override
		public void update(Member previous, Member updated) {
			all.replaceAll((m) -> m.memberId().equals(previous.memberId()) ? updated : m);
		}

	}

	static final class Events implements GatewayEventRepository {

		final Map<String, GatewayEvent> byId = new ConcurrentHashMap<>();

		final Map<String, Instant> claimedAt = new ConcurrentHashMap<>();

		boolean failNextFailedRecord;

		@Override
		public Optional<GatewayEvent> findById(String eventId) {
			return Optional.ofNullable(byId.get(eventId));
		}

		@Override
		public boolean insertReceived(GatewayEvent event) {
			return byId.putIfAbsent(event.eventId(), event) == null;
		}

		@Override
		public boolean claimRetry(String eventId, Instant now, Instant staleBefore) {
			GatewayEvent e = byId.get(eventId);
			if (e == null) {
				return false;
			}
			boolean stale = e.status() == GatewayEventStatus.RECEIVED
					&& claimedAt.getOrDefault(eventId, e.receivedAt()).isBefore(staleBefore);
			if (e.status() != GatewayEventStatus.FAILED && !stale) {
				return false;
			}
			claimedAt.put(eventId, now);
			byId.put(eventId, new GatewayEvent(e.eventId(), e.gateway(), e.type(), e.orderId(), e.chargeId(),
					GatewayEventStatus.RECEIVED, e.reason(), e.tenantId(), e.attempts() + 1, e.receivedAt(),
					e.processedAt()));
			return true;
		}

		@Override
		public void complete(String eventId, GatewayEventStatus status, String reason, String tenantId, Instant now) {
			if (status == GatewayEventStatus.FAILED && failNextFailedRecord) {
				failNextFailedRecord = false;
				throw new IllegalStateException("database down");
			}
			GatewayEvent e = byId.get(eventId);
			byId.put(eventId, new GatewayEvent(e.eventId(), e.gateway(), e.type(), e.orderId(), e.chargeId(), status,
					reason, tenantId, e.attempts(), e.receivedAt(), now));
		}

	}

}
