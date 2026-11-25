package br.com.lhamalog.core.ingestiontoken.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/** In-memory {@link IngestionTokenRepository} with the same unique-hash and version rules as the Mongo adapter. */
public final class InMemoryIngestionTokenRepository implements IngestionTokenRepository {
	private final Map<String, IngestionToken> tokens = new LinkedHashMap<>();
	private int updates;

	@Override
	public IngestionToken insert(IngestionToken token) {
		if (tokens.containsKey(token.id())
				|| tokens.values().stream().anyMatch(existing -> existing.tokenHash().equals(token.tokenHash()))) {
			throw new DuplicateKeyException("duplicate token");
		}
		IngestionToken stored = withVersion(token, 0L);
		tokens.put(stored.id(), stored);
		return stored;
	}

	@Override
	public IngestionToken update(IngestionToken token) {
		IngestionToken current = tokens.get(token.id());
		if (current == null || !current.version().equals(token.version())) {
			throw new OptimisticLockingFailureException("stale token");
		}
		IngestionToken stored = withVersion(token, token.version() + 1);
		tokens.put(stored.id(), stored);
		updates++;
		return stored;
	}

	@Override
	public Optional<IngestionToken> find(String tenantId, String productId, String tokenId) {
		return Optional.ofNullable(tokens.get(tokenId))
				.filter(token -> token.tenantId().equals(tenantId) && token.productId().equals(productId));
	}

	@Override
	public List<IngestionToken> list(String tenantId, String productId) {
		List<IngestionToken> result = new ArrayList<>();
		for (IngestionToken token : tokens.values()) {
			if (token.tenantId().equals(tenantId) && token.productId().equals(productId)) result.add(0, token);
		}
		result.sort(Comparator.comparing(IngestionToken::createdAt).reversed());
		return result;
	}

	public List<IngestionToken> all() { return List.copyOf(tokens.values()); }

	public int updates() { return updates; }

	private static IngestionToken withVersion(IngestionToken t, Long version) {
		return new IngestionToken(t.id(), t.tenantId(), t.productId(), t.name(), t.tokenHash(), t.status(), t.createdAt(),
				t.createdBy(), t.revokedAt(), t.revokedBy(), t.expiresAt(), t.replacedBy(), version);
	}
}
