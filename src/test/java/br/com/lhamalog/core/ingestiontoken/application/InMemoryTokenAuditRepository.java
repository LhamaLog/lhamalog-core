package br.com.lhamalog.core.ingestiontoken.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** In-memory {@link TokenAuditRepository}; entries with the same instant keep the latest appended first. */
public final class InMemoryTokenAuditRepository implements TokenAuditRepository {
	private final List<TokenAuditEntry> entries = new ArrayList<>();

	@Override
	public void append(TokenAuditEntry entry) {
		entries.add(entry);
	}

	@Override
	public List<TokenAuditEntry> list(String tenantId, String productId) {
		List<TokenAuditEntry> result = new ArrayList<>();
		for (TokenAuditEntry entry : entries) {
			if (entry.tenantId().equals(tenantId) && entry.productId().equals(productId)) result.add(0, entry);
		}
		result.sort(Comparator.comparing(TokenAuditEntry::at).reversed());
		return result;
	}

	public List<TokenAuditEntry> all() { return List.copyOf(entries); }
}
