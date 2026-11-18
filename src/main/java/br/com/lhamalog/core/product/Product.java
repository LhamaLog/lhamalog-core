package br.com.lhamalog.core.product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

/**
 * Product of the active company. {@code archivedAt} null means active; filled means archived.
 * Documents saved before this field existed have no {@code archivedAt} and count as active.
 * {@code normalizedName} is the form of the name that must be unique per company; archived products keep it.
 */
@Document("Produto")
@CompoundIndexes({
		@CompoundIndex(name = "product_tenant_name", def = "{'tenantId': 1, 'name': 1, '_id': 1}"),
		@CompoundIndex(name = "product_tenant_normalized_name", def = "{'tenantId': 1, 'normalizedName': 1}", unique = true)
})
public record Product(@Id String id, String tenantId, String name, @JsonIgnore String normalizedName, String description,
		String category, String productVersion, String status, Instant createdAt, Instant archivedAt,
		Context context, @Version Long version) {
	/** Comparison form of a name: no surrounding spaces and lower case, whatever the JVM default locale is. */
	public static String normalizeName(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	/** Level of detail the AI should use when answering about this product. */
	public enum Personality {
		SIMPLE("Simples"), STANDARD("Padrão"), DETAILED("Detalhado");

		private final String label;

		Personality(String label) { this.label = label; }

		public String label() { return label; }
	}

	/**
	 * Context of the product for the AI (feature 009), with the fields of the "Contexto para IA" screen.
	 * Every field is optional. The compact constructor trims texts, drops blank list items and applies
	 * the defaults, both for request bodies and for documents read from MongoDB.
	 */
	public record Context(
			@Size(max = 2000) String functionalDescription,
			Personality personality,
			@Size(max = 20) @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$") String responseLanguage,
			@Size(max = 30) List<@Size(max = 80) String> architecture,
			@Size(max = 30) List<@Size(max = 200) String> scope,
			@Size(max = 30) List<@Size(max = 200) String> sensitiveData) {

		public static final String DEFAULT_LANGUAGE = "pt-BR";

		public Context {
			functionalDescription = functionalDescription == null ? "" : functionalDescription.trim();
			personality = personality == null ? Personality.STANDARD : personality;
			responseLanguage = responseLanguage == null || responseLanguage.isBlank() ? DEFAULT_LANGUAGE
					: responseLanguage.trim();
			architecture = clean(architecture);
			scope = clean(scope);
			sensitiveData = clean(sensitiveData);
		}

		/** Context with no content and the default personality and language. */
		public static Context empty() {
			return new Context(null, null, null, null, null, null);
		}

		/** True when the description or any list has content; personality and language alone do not count. */
		public boolean hasContent() {
			return !functionalDescription.isEmpty() || !architecture.isEmpty() || !scope.isEmpty()
					|| !sensitiveData.isEmpty();
		}

		private static List<String> clean(List<String> values) {
			if (values == null || values.isEmpty()) return List.of();
			List<String> cleaned = new ArrayList<>();
			for (String value : values) {
				if (value == null) continue;
				String trimmed = value.trim();
				if (!trimmed.isEmpty()) cleaned.add(trimmed);
			}
			return List.copyOf(cleaned);
		}
	}
}
