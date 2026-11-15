package br.com.lhamalog.core.ingestiontoken.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import br.com.lhamalog.core.ingestiontoken.application.IngestionToken;
import br.com.lhamalog.core.ingestiontoken.application.TokenSecrets;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Checks the raw documents of {@code product_tokens} against the contract the Collector reads
 * ({@code MongoProductTokenStore}, LLT-181): field names, types, status values and the unique hash.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=${TEST_MONGODB_URI}",
		"management.otlp.metrics.export.enabled=false", "management.tracing.export.otlp.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class MongoIngestionTokenRepositoryIntegrationTest {
	@Autowired MongoIngestionTokenRepository repository;
	@Autowired MongoTemplate mongo;
	private final TokenSecrets secrets = new TokenSecrets();

	private IngestionToken newToken(String secret) {
		return IngestionToken.issue(UUID.randomUUID().toString(), "org_test_" + UUID.randomUUID(),
				UUID.randomUUID().toString(), "cluster", TokenSecrets.sha256Hex(secret),
				Instant.now().truncatedTo(ChronoUnit.MILLIS), "admin_1");
	}

	private Document raw(String id) {
		return mongo.findOne(Query.query(Criteria.where("_id").is(id)), Document.class, "product_tokens");
	}

	/** Same rule as the Collector's ProductTokenValidity, applied to the raw document. */
	private static boolean collectorAccepts(Document document, Instant now) {
		Object expires = document.get("expiresAt");
		return "ACTIVE".equals(document.getString("status")) && document.get("revokedAt") == null
				&& (expires == null || ((Date) expires).toInstant().isAfter(now));
	}

	@Test
	void activeTokenIsStoredInTheShapeTheCollectorReads() {
		String secret = secrets.generate();
		IngestionToken token = repository.insert(newToken(secret));

		Document document = raw(token.id());
		assertNotNull(document);
		assertInstanceOf(String.class, document.get("_id"));
		assertEquals(token.productId(), document.getString("productId"));
		assertEquals(TokenSecrets.sha256Hex(secret), document.getString("tokenHash"));
		assertEquals("ACTIVE", document.getString("status"));
		assertInstanceOf(Date.class, document.get("createdAt"));
		assertFalse(document.containsKey("revokedAt"));
		assertFalse(document.containsKey("expiresAt"));
		assertFalse(document.toJson().contains(secret));
		assertTrue(collectorAccepts(document, Instant.now()));
	}

	@Test
	void revokedAndExpiredTokensAreRefusedByTheCollectorRule() {
		IngestionToken revoked = repository.insert(newToken(secrets.generate()));
		repository.update(revoked.revoke(Instant.now().truncatedTo(ChronoUnit.MILLIS), "admin_1"));
		Document revokedDocument = raw(revoked.id());
		assertEquals("REVOKED", revokedDocument.getString("status"));
		assertInstanceOf(Date.class, revokedDocument.get("revokedAt"));
		assertFalse(collectorAccepts(revokedDocument, Instant.now()));

		IngestionToken rotating = repository.insert(newToken(secrets.generate()));
		Instant end = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
		repository.update(rotating.rotateOut(end, "next-id"));
		Document rotatingDocument = raw(rotating.id());
		assertTrue(collectorAccepts(rotatingDocument, Instant.now()));
		assertFalse(collectorAccepts(rotatingDocument, end));
		assertEquals("next-id", rotatingDocument.getString("replacedBy"));
	}

	@Test
	void hashIsUniqueAndStaleUpdatesConflict() {
		String secret = secrets.generate();
		IngestionToken token = repository.insert(newToken(secret));
		assertThrows(DuplicateKeyException.class, () -> repository.insert(newToken(secret)));

		IngestionToken first = repository.update(token.revoke(Instant.now().truncatedTo(ChronoUnit.MILLIS), "a"));
		assertTrue(first.version() > token.version());
		assertThrows(OptimisticLockingFailureException.class,
				() -> repository.update(token.rotateOut(Instant.now().plusSeconds(60), "x")));
	}

	@Test
	void listAndFindStayInsideTheTenantAndProduct() {
		IngestionToken token = repository.insert(newToken(secrets.generate()));
		assertTrue(repository.find(token.tenantId(), token.productId(), token.id()).isPresent());
		assertTrue(repository.find("org_other", token.productId(), token.id()).isEmpty());
		assertTrue(repository.find(token.tenantId(), "other-product", token.id()).isEmpty());
		assertEquals(1, repository.list(token.tenantId(), token.productId()).size());
	}
}
