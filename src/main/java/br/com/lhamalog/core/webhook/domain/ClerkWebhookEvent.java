package br.com.lhamalog.core.webhook.domain;

import java.time.Instant;
import java.util.Objects;

import br.com.lhamalog.core.member.domain.MembershipSnapshot;
import br.com.lhamalog.core.member.domain.UserProfile;

/** A Clerk webhook already interpreted into what the Core cares about. */
public sealed interface ClerkWebhookEvent {

	/** Clerk event type, e.g. {@code user.updated}. */
	String type();

	record UserUpserted(String type, UserProfile profile) implements ClerkWebhookEvent {

		public UserUpserted {
			Objects.requireNonNull(profile, "profile");
		}

	}

	record UserDeleted(String type, String clerkUserId, Instant deletedAt) implements ClerkWebhookEvent {

		public UserDeleted {
			Objects.requireNonNull(clerkUserId, "clerkUserId");
			Objects.requireNonNull(deletedAt, "deletedAt");
		}

	}

	record MembershipUpserted(String type, MembershipSnapshot membership) implements ClerkWebhookEvent {

		public MembershipUpserted {
			Objects.requireNonNull(membership, "membership");
		}

	}

	record MembershipDeleted(String type, MembershipSnapshot membership, Instant removedAt)
			implements ClerkWebhookEvent {

		public MembershipDeleted {
			Objects.requireNonNull(membership, "membership");
			Objects.requireNonNull(removedAt, "removedAt");
		}

	}

	/** Event type the Core does not handle: acknowledged and ignored. */
	record Unhandled(String type) implements ClerkWebhookEvent {

	}

}
