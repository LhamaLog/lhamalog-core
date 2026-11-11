package br.com.lhamalog.core.usage.infrastructure;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("UsageFactIdempotency")
public record UsageFactIdempotencyDocument(
		@Id String id,
		String batchId,
		String tenantId,
		String productId,
		String domain,
		List<String> acceptedRecordIds,
		int acceptedRecordCount,
		long acceptedBytes,
		Instant acceptedAt,
		int schemaVersion,
		Instant receivedAt) {}
