package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.util.List;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditEntry;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditRepository;
import org.springframework.stereotype.Repository;

/** Mongo adapter for {@link TokenAuditRepository}: insert only. */
@Repository
public class MongoTokenAuditRepository implements TokenAuditRepository {
	private final TokenAuditMongoRepository documents;

	public MongoTokenAuditRepository(TokenAuditMongoRepository documents) {
		this.documents = documents;
	}

	@Override
	public void append(TokenAuditEntry entry) {
		documents.insert(TokenAuditDocument.from(entry));
	}

	@Override
	public List<TokenAuditEntry> list(String tenantId, String productId) {
		return documents.findByTenantIdAndProductIdOrderByAtDesc(tenantId, productId).stream()
				.map(TokenAuditDocument::toEntry).toList();
	}
}
