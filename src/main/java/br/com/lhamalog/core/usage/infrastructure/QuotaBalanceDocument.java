package br.com.lhamalog.core.usage.infrastructure;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("usage_quota_balances")
public record QuotaBalanceDocument(@Id String id, String tenantId, String productId, Instant cycleStart,
		long allowanceBytes, long reservedBytes, long consumedBytes) {
}
