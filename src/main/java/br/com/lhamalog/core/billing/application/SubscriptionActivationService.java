package br.com.lhamalog.core.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.lhamalog.core.billing.application.port.out.SubscriptionOrderRepository;
import br.com.lhamalog.core.billing.application.port.out.TenantSubscriptionRepository;
import br.com.lhamalog.core.billing.domain.GatewayEventStatus;
import br.com.lhamalog.core.billing.domain.GatewayNotification;
import br.com.lhamalog.core.billing.domain.SubscriptionOrder;
import br.com.lhamalog.core.billing.domain.TenantSubscription;
import br.com.lhamalog.core.member.application.port.out.MemberRepository;
import br.com.lhamalog.core.member.domain.Member;
import br.com.lhamalog.core.member.domain.MemberRole;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning;
import br.com.lhamalog.core.tenant.application.port.out.TenantProvisioning.ProvisionedTenant;

/**
 * Turns a confirmed payment into an active tenant (ADR_121, spec 010 FR-009). There is no transaction (standalone
 * MongoDB), so every step is idempotent and the steps run in an order that a resend can resume (research R5): tenant,
 * subscription, DONO member, paid order.
 */
public class SubscriptionActivationService {

	static final String UNKNOWN_ORDER = "unknown_order";

	static final String ORDER_ALREADY_PAID = "order_already_paid";

	static final String TENANT_ALREADY_ACTIVE = "tenant_already_active";

	private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationService.class);

	private final SubscriptionOrderRepository orders;

	private final TenantProvisioning tenants;

	private final TenantSubscriptionRepository subscriptions;

	private final MemberRepository members;

	private final Clock clock;

	private final Supplier<String> idGenerator;

	public SubscriptionActivationService(SubscriptionOrderRepository orders, TenantProvisioning tenants,
			TenantSubscriptionRepository subscriptions, MemberRepository members, Clock clock) {
		this(orders, tenants, subscriptions, members, clock, () -> UUID.randomUUID().toString());
	}

	SubscriptionActivationService(SubscriptionOrderRepository orders, TenantProvisioning tenants,
			TenantSubscriptionRepository subscriptions, MemberRepository members, Clock clock,
			Supplier<String> idGenerator) {
		this.orders = orders;
		this.tenants = tenants;
		this.subscriptions = subscriptions;
		this.members = members;
		this.clock = clock;
		this.idGenerator = idGenerator;
	}

	/**
	 * Activates the order of a payment confirmation.
	 * @return how the event ends; a failure that a resend can fix is thrown instead
	 */
	public Outcome activate(GatewayNotification confirmation) {
		Optional<SubscriptionOrder> found = (confirmation.orderId() != null)
				? orders.findById(confirmation.orderId()) : Optional.empty();
		if (found.isEmpty()) {
			log.warn("event=billing.activation.unknown_order | Payment confirmation for an unknown order"
					+ " | eventId={} chargeId={}", confirmation.eventId(), confirmation.chargeId());
			return new Outcome(GatewayEventStatus.FAILED, UNKNOWN_ORDER, null);
		}
		SubscriptionOrder order = found.get();
		if (order.isPaid()) {
			log.info("event=billing.webhook.ignored | Order already paid | eventId={} orderId={}",
					confirmation.eventId(), order.orderId());
			return new Outcome(GatewayEventStatus.IGNORED, ORDER_ALREADY_PAID, null);
		}
		warnOnValueMismatch(confirmation, order);
		Instant now = clock.instant();
		ProvisionedTenant tenant = tenants.provisionForOrder(order.clerkOrgId(), order.companyName(),
				order.orderId());
		if (!tenant.createdBy(order.orderId())) {
			orders.markPaid(order.orderId(), now);
			log.warn("event=billing.activation.already_active | Payment confirmed for an organization that already has"
					+ " a tenant; nothing created, needs manual handling | eventId={} orderId={} clerkOrgId={}"
					+ " tenantId={}", confirmation.eventId(), order.orderId(), order.clerkOrgId(), tenant.tenantId());
			return new Outcome(GatewayEventStatus.IGNORED, TENANT_ALREADY_ACTIVE, tenant.tenantId());
		}
		subscriptions.createIfAbsent(TenantSubscription.activated(tenant.tenantId(), order, now));
		ensureOwner(tenant.tenantId(), order.clerkUserId());
		orders.markPaid(order.orderId(), now);
		log.info("event=billing.activation.executed | Tenant activated by payment | eventId={} orderId={}"
				+ " clerkOrgId={} tenantId={} plan={}", confirmation.eventId(), order.orderId(), order.clerkOrgId(),
				tenant.tenantId(), order.planTier());
		return new Outcome(GatewayEventStatus.EXECUTED, null, tenant.tenantId());
	}

	private void ensureOwner(String tenantId, String clerkUserId) {
		Optional<Member> current = members.findByTenantIdAndClerkUserId(tenantId, clerkUserId);
		if (current.isEmpty()) {
			try {
				members.insert(Member.owner(idGenerator.get(), tenantId, clerkUserId));
				return;
			}
			catch (RuntimeException ex) {
				// A Clerk membership event created the member in the meantime: promote it below.
				current = members.findByTenantIdAndClerkUserId(tenantId, clerkUserId);
				if (current.isEmpty()) {
					throw ex;
				}
			}
		}
		Member member = current.get();
		if (member.role() != MemberRole.DONO) {
			members.update(member, member.promotedToOwner());
		}
	}

	private static void warnOnValueMismatch(GatewayNotification confirmation, SubscriptionOrder order) {
		if (confirmation.value() != null && confirmation.value().compareTo(order.price()) != 0) {
			log.warn("event=billing.activation.value_mismatch | Confirmed value differs from the plan price; the plan of"
					+ " the order is used | eventId={} orderId={} plan={}", confirmation.eventId(), order.orderId(),
					order.planTier());
		}
	}

	/**
	 * @param reason why it was ignored or failed; {@code null} when executed
	 * @param tenantId tenant concerned, when known
	 */
	public record Outcome(GatewayEventStatus status, String reason, String tenantId) {
	}

}
