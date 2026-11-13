package br.com.lhamalog.core.webhook.adapter.in.web;

import java.time.Instant;

import br.com.lhamalog.core.member.domain.MembershipSnapshot;
import br.com.lhamalog.core.member.domain.UserProfile;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the raw (already verified) Clerk webhook body into a {@link ClerkWebhookEvent}. Reads only the fields the
 * Core needs; unknown types become {@link ClerkWebhookEvent.Unhandled}.
 */
public final class ClerkWebhookEventParser {

	/** The signed body is not a well-formed event of a handled type. */
	public static final class InvalidWebhookPayloadException extends RuntimeException {

		InvalidWebhookPayloadException(String message) {
			super(message);
		}

	}

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public ClerkWebhookEvent parse(byte[] body) {
		JsonNode root;
		try {
			root = jsonMapper.readTree(body);
		}
		catch (JacksonException ex) {
			throw new InvalidWebhookPayloadException("Body is not JSON");
		}
		if (root == null || !root.isObject()) {
			throw new InvalidWebhookPayloadException("Body is not a JSON object");
		}
		String type = required(text(root, "type"), "type");
		JsonNode data = root.get("data");
		return switch (type) {
			case "user.created", "user.updated" -> new ClerkWebhookEvent.UserUpserted(type, userProfile(data(data)));
			case "user.deleted" -> new ClerkWebhookEvent.UserDeleted(type, required(text(data(data), "id"), "data.id"),
					eventTime(root));
			case "organizationMembership.created", "organizationMembership.updated" ->
				new ClerkWebhookEvent.MembershipUpserted(type, membership(data(data), version(data(data))));
			case "organizationMembership.deleted" -> {
				Instant removedAt = eventTime(root);
				yield new ClerkWebhookEvent.MembershipDeleted(type, membership(data(data), removedAt.toEpochMilli()),
						removedAt);
			}
			default -> new ClerkWebhookEvent.Unhandled(type);
		};
	}

	private static UserProfile userProfile(JsonNode data) {
		return new UserProfile(required(text(data, "id"), "data.id"),
				fullName(text(data, "first_name"), text(data, "last_name")), primaryEmail(data), version(data));
	}

	private static MembershipSnapshot membership(JsonNode data, long version) {
		JsonNode organization = data.get("organization");
		JsonNode user = data.get("public_user_data");
		String organizationId = (organization != null) ? text(organization, "id") : null;
		String userId = (user != null) ? text(user, "user_id") : null;
		String identifier = (user != null) ? text(user, "identifier") : null;
		return new MembershipSnapshot(required(organizationId, "data.organization.id"),
				required(userId, "data.public_user_data.user_id"), text(data, "id"), text(data, "role"),
				(user != null) ? fullName(text(user, "first_name"), text(user, "last_name")) : null,
				(identifier != null && identifier.contains("@")) ? identifier : null, version);
	}

	private static String primaryEmail(JsonNode data) {
		String primaryId = text(data, "primary_email_address_id");
		JsonNode addresses = data.get("email_addresses");
		if (primaryId == null || addresses == null || !addresses.isArray()) {
			return null;
		}
		for (JsonNode address : addresses) {
			if (primaryId.equals(text(address, "id"))) {
				return text(address, "email_address");
			}
		}
		return null;
	}

	private static String fullName(String first, String last) {
		String name = ((first != null) ? first.trim() : "") + " " + ((last != null) ? last.trim() : "");
		return name.isBlank() ? null : name.trim();
	}

	private static long version(JsonNode data) {
		JsonNode updatedAt = data.get("updated_at");
		if (updatedAt == null || !updatedAt.isIntegralNumber()) {
			throw new InvalidWebhookPayloadException("Missing data.updated_at");
		}
		return updatedAt.asLong();
	}

	private static Instant eventTime(JsonNode root) {
		JsonNode timestamp = root.get("timestamp");
		if (timestamp == null || !timestamp.isIntegralNumber()) {
			throw new InvalidWebhookPayloadException("Missing timestamp");
		}
		return Instant.ofEpochMilli(timestamp.asLong());
	}

	private static JsonNode data(JsonNode data) {
		if (data == null || !data.isObject()) {
			throw new InvalidWebhookPayloadException("Missing data");
		}
		return data;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value != null && value.isString()) ? value.asString() : null;
	}

	private static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new InvalidWebhookPayloadException("Missing " + field);
		}
		return value;
	}

}
