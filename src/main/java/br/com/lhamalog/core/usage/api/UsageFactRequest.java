package br.com.lhamalog.core.usage.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;

public record UsageFactRequest(
		@NotBlank String usageFactId,
		@NotBlank String batchId,
		@NotBlank String tenantId,
		@NotBlank String productId,
		@NotNull UsageDomain domain,
		@NotEmpty List<@NotBlank String> acceptedRecordIds,
		@Min(1) int acceptedRecordCount,
		@Min(0) long acceptedBytes,
		@NotNull OffsetDateTime acceptedAt,
		@Min(1) int schemaVersion) {

	@AssertTrue(message = "acceptedRecordIds must be unique and match acceptedRecordCount")
	public boolean hasValidRecords() {
		return acceptedRecordIds != null && acceptedRecordCount == acceptedRecordIds.size()
				&& new HashSet<>(acceptedRecordIds).size() == acceptedRecordIds.size();
	}

	@AssertTrue(message = "Only schema version 1 is supported")
	public boolean hasSupportedSchemaVersion() {
		return schemaVersion == 1;
	}

	@AssertTrue(message = "acceptedAt must use UTC")
	public boolean hasUtcAcceptedAt() {
		return acceptedAt != null && acceptedAt.getOffset().getTotalSeconds() == 0;
	}
}
