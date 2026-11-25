package br.com.lhamalog.core.ingestiontoken.infrastructure;

import java.util.List;
import java.util.Optional;
import br.com.lhamalog.core.ingestiontoken.application.IngestionToken;
import br.com.lhamalog.core.ingestiontoken.application.IngestionTokenRepository;
import org.springframework.stereotype.Repository;

/** Mongo adapter for {@link IngestionTokenRepository}. {@code @Version} turns concurrent changes into a conflict. */
@Repository
public class MongoIngestionTokenRepository implements IngestionTokenRepository {
	private final IngestionTokenMongoRepository documents;

	public MongoIngestionTokenRepository(IngestionTokenMongoRepository documents) {
		this.documents = documents;
	}

	@Override
	public IngestionToken insert(IngestionToken token) {
		return documents.insert(IngestionTokenDocument.from(token)).toToken();
	}

	@Override
	public IngestionToken update(IngestionToken token) {
		return documents.save(IngestionTokenDocument.from(token)).toToken();
	}

	@Override
	public Optional<IngestionToken> find(String tenantId, String productId, String tokenId) {
		return documents.findByIdAndTenantIdAndProductId(tokenId, tenantId, productId).map(IngestionTokenDocument::toToken);
	}

	@Override
	public List<IngestionToken> list(String tenantId, String productId) {
		return documents.findByTenantIdAndProductIdOrderByCreatedAtDesc(tenantId, productId).stream()
				.map(IngestionTokenDocument::toToken).toList();
	}
}
