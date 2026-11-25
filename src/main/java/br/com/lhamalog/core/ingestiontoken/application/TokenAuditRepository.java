package br.com.lhamalog.core.ingestiontoken.application;

import java.util.List;

/** Port for the {@code ProductTokenAudit} collection. Entries are only appended, never changed. */
public interface TokenAuditRepository {
	void append(TokenAuditEntry entry);

	/** Entries of the product, newest first. */
	List<TokenAuditEntry> list(String tenantId, String productId);
}
