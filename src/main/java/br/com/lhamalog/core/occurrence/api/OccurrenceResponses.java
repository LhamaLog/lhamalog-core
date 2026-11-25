package br.com.lhamalog.core.occurrence.api;

import java.time.Instant;
import java.util.List;

/** Response bodies of {@code /api/occurrences} ({@code specs/012-consultar-ocorrencias/contracts/api.md}). */
public final class OccurrenceResponses {

	private OccurrenceResponses() {
	}

	public record Page(List<ListItem> items, int page, int size, long totalItems) {}

	public record ProductRef(String id, String name) {}

	public record ListItem(String id, String type, String title, ProductRef product, long count, String status,
			boolean recurring, Instant firstSeenAt, Instant lastSeenAt) {}

	public record Detail(String id, String type, String title, String summary, ProductRef product, long count,
			String status, boolean recurring, Instant firstSeenAt, Instant lastSeenAt, Boolean sensitiveData,
			List<String> samples, List<Attribute> attributes, List<StatusChange> statusHistory) {}

	public record Attribute(String name, String value) {}

	public record StatusChange(String from, String to, Instant changedAt, ChangedBy changedBy) {}

	public record ChangedBy(String type, String id) {}
}
