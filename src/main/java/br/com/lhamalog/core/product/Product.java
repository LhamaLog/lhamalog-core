package br.com.lhamalog.core.product;

import java.time.Instant;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("Produto")
@CompoundIndex(name = "product_tenant_name", def = "{'tenantId': 1, 'name': 1, '_id': 1}")
public record Product(@Id String id, String tenantId, String name, String description,
		String category, String productVersion, String status, Instant createdAt,
		Context context, @Version Long version) {
	public record Context(@Size(max = 10000) String problemSolved, @Size(max = 10000) String targetAudience,
			@Size(max = 10000) String differentiators, @Size(max = 10000) String keywords,
			@Size(max = 10000) String restrictions) {}
}
