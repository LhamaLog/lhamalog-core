package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.time.Instant;
import br.com.lhamalog.core.ingestiontoken.application.IngestionToken;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Document of {@code product_tokens}, the collection the Collector reads (LLT-181). The fields {@code _id},
 * {@code productId}, {@code tokenHash}, {@code status}, {@code createdAt}, {@code revokedAt} and
 * {@code expiresAt} are its contract and must keep their names and types; the others are ignored by it.
 */
@Document("product_tokens")
@CompoundIndex(name = "product_token_tenant_product", def = "{'tenantId': 1, 'productId': 1, 'createdAt': -1}")
public record IngestionTokenDocument(
		@Id String id,
		String productId,
		@Indexed(name = "product_token_hash", unique = true) String tokenHash,
		IngestionToken.Status status,
		Instant createdAt,
		Instant revokedAt,
		Instant expiresAt,
		String tenantId,
		String name,
		String createdBy,
		String revokedBy,
		String replacedBy,
		@Version Long version) {

	static IngestionTokenDocument from(IngestionToken token) {
		return new IngestionTokenDocument(token.id(), token.productId(), token.tokenHash(), token.status(),
				token.createdAt(), token.revokedAt(), token.expiresAt(), token.tenantId(), token.name(),
				token.createdBy(), token.revokedBy(), token.replacedBy(), token.version());
	}

	IngestionToken toToken() {
		return new IngestionToken(id, tenantId, productId, name, tokenHash, status, createdAt, createdBy, revokedAt,
				revokedBy, expiresAt, replacedBy, version);
	}
}
