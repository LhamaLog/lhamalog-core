package br.com.lhamalog.core.ingestiontoken.application;

import java.util.List;
import java.util.Optional;

/** Port for the {@code product_tokens} collection. */
public interface IngestionTokenRepository {
	/** Inserts a new token; a duplicate hash must fail. */
	IngestionToken insert(IngestionToken token);

	/** Saves a change to an existing token; a stale {@code version} must fail with OptimisticLockingFailureException. */
	IngestionToken update(IngestionToken token);

	Optional<IngestionToken> find(String tenantId, String productId, String tokenId);

	/** Tokens of the product, newest first. */
	List<IngestionToken> list(String tenantId, String productId);
}
