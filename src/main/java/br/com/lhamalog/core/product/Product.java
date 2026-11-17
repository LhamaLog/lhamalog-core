package br.com.lhamalog.core.product;

import java.time.Instant;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

	public record Context(@Size(max = 10000) String problemSolved, @Size(max = 10000) String targetAudience,
			@Size(max = 10000) String differentiators, @Size(max = 10000) String keywords,
			@Size(max = 10000) String restrictions) {}
}
