package br.com.lhamalog.core.webhook.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.lhamalog.core.member.application.MemberSynchronizer;
import br.com.lhamalog.core.webhook.application.port.out.ProcessedWebhookRepository;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent.MembershipDeleted;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent.MembershipUpserted;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent.Unhandled;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent.UserDeleted;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent.UserUpserted;

/**
 * Applies an authentic Clerk webhook exactly once per {@code svix-id}. The id is recorded only after a successful
 * processing, so a failure (exception) lets Svix retry; handlers are idempotent, so a concurrent duplicate is safe.
 */
public class ClerkWebhookProcessor {

	public enum Outcome {

		PROCESSED,

		DUPLICATE

	}

	private static final Logger log = LoggerFactory.getLogger(ClerkWebhookProcessor.class);

	private final ProcessedWebhookRepository processedWebhooks;

	private final MemberSynchronizer memberSynchronizer;

	private final Clock clock;

	public ClerkWebhookProcessor(ProcessedWebhookRepository processedWebhooks, MemberSynchronizer memberSynchronizer,
			Clock clock) {
		this.processedWebhooks = processedWebhooks;
		this.memberSynchronizer = memberSynchronizer;
		this.clock = clock;
	}

	public Outcome process(String svixId, ClerkWebhookEvent event) {
		if (processedWebhooks.isProcessed(svixId)) {
			log.debug("Duplicate Clerk webhook svixId={} type={}", svixId, event.type());
			return Outcome.DUPLICATE;
		}
		Optional<String> tenantId = switch (event) {
			case UserUpserted user -> {
				memberSynchronizer.syncUserProfile(user.profile());
				yield Optional.empty();
			}
			case UserDeleted user -> {
				memberSynchronizer.deleteUser(user.clerkUserId(), user.deletedAt());
				yield Optional.empty();
			}
			case MembershipUpserted membership -> memberSynchronizer.syncMembership(membership.membership());
			case MembershipDeleted membership ->
				memberSynchronizer.removeMembership(membership.membership(), membership.removedAt());
			case Unhandled unhandled -> {
				log.debug("Ignoring unhandled Clerk webhook type={}", unhandled.type());
				yield Optional.empty();
			}
		};
		processedWebhooks.markProcessed(svixId, event.type(), tenantId.orElse(null), clock.instant());
		log.info("Processed Clerk webhook svixId={} type={}", svixId, event.type());
		return Outcome.PROCESSED;
	}

}
