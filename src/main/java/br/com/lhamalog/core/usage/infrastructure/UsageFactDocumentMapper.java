package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import java.time.ZoneOffset;

public final class UsageFactDocumentMapper {
	private UsageFactDocumentMapper() {}

	public static UsageFactDocument toDocument(UsageFactRepository.StoredUsageFact fact) {
		UsageFactRequest request = fact.request();
		return new UsageFactDocument(null, request.usageFactId(), request.batchId(), request.tenantId(),
				request.productId(), request.domain().name(), request.acceptedRecordIds(),
				request.acceptedRecordCount(), request.acceptedBytes(), request.acceptedAt().toInstant(),
				request.schemaVersion(), fact.receivedAt());
	}

	public static UsageFactIdempotencyDocument toIdempotency(UsageFactRepository.StoredUsageFact fact) {
		UsageFactRequest request = fact.request();
		return new UsageFactIdempotencyDocument(request.usageFactId(), request.batchId(), request.tenantId(),
				request.productId(), request.domain().name(), request.acceptedRecordIds(),
				request.acceptedRecordCount(), request.acceptedBytes(), request.acceptedAt().toInstant(),
				request.schemaVersion(), fact.receivedAt());
	}

	public static UsageFactRepository.StoredUsageFact toStored(UsageFactDocument document) {
		return new UsageFactRepository.StoredUsageFact(toRequest(document), document.receivedAt());
	}

	public static UsageFactRepository.StoredUsageFact toStored(UsageFactIdempotencyDocument document) {
		UsageFactRequest request = new UsageFactRequest(document.id(), document.batchId(), document.tenantId(),
				document.productId(), UsageDomain.valueOf(document.domain()), document.acceptedRecordIds(),
				document.acceptedRecordCount(), document.acceptedBytes(),
				document.acceptedAt().atOffset(ZoneOffset.UTC), document.schemaVersion());
		return new UsageFactRepository.StoredUsageFact(request, document.receivedAt());
	}

	public static UsageFactRequest toRequest(UsageFactDocument document) {
		return new UsageFactRequest(document.usageFactId(), document.batchId(), document.tenantId(),
				document.productId(), UsageDomain.valueOf(document.domain()), document.acceptedRecordIds(),
				document.acceptedRecordCount(), document.acceptedBytes(),
				document.acceptedAt().atOffset(ZoneOffset.UTC), document.schemaVersion());
	}
}
