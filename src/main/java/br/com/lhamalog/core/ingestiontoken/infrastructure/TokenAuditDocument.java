package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.time.Instant;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditEntry;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Document of {@code ProductTokenAudit}. Never holds the token value or hash. */
@Document("ProductTokenAudit")
@CompoundIndex(name = "token_audit_tenant_product", def = "{'tenantId': 1, 'productId': 1, 'at': -1}")
public record TokenAuditDocument(@Id String id, String tenantId, String productId, String tokenId,
		TokenAuditEntry.Action action, String actorUserId, Instant at, String relatedTokenId, Instant transitionEndsAt) {

	static TokenAuditDocument from(TokenAuditEntry entry) {
		return new TokenAuditDocument(entry.id(), entry.tenantId(), entry.productId(), entry.tokenId(), entry.action(),
				entry.actorUserId(), entry.at(), entry.relatedTokenId(), entry.transitionEndsAt());
	}

	TokenAuditEntry toEntry() {
		return new TokenAuditEntry(id, tenantId, productId, tokenId, action, actorUserId, at, relatedTokenId,
				transitionEndsAt);
	}
}
