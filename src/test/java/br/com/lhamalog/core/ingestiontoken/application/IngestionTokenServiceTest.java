package br.com.lhamalog.core.ingestiontoken.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import br.com.lhamalog.core.ingestiontoken.application.IngestionToken.State;
import br.com.lhamalog.core.ingestiontoken.application.IngestionTokenService.CreatedToken;
import br.com.lhamalog.core.ingestiontoken.application.IngestionTokenService.Rotation;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditEntry.Action;
import br.com.lhamalog.core.shared.CompanySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests of the token rules, with in-memory repositories and a controlled clock. No database. */
class IngestionTokenServiceTest {
	private static final String TENANT = "org_a";
	private static final String PRODUCT = "product-1";
	private static final CompanySession ADMIN = new CompanySession(TENANT, "admin_1", true);
	private static final CompanySession MEMBER = new CompanySession(TENANT, "member_1", false);

	/** Clock the tests move by hand. */
	static final class MutableClock implements InstantSource {
		private Instant now = Instant.parse("2026-11-12T12:00:00Z");
		@Override public Instant instant() { return now; }
		void advance(Duration duration) { now = now.plus(duration); }
	}

	/** Products of the tests: tenant + id → archived flag. */
	static final class FakeCatalog implements ProductCatalog {
		final Map<String, Boolean> products = new HashMap<>();
		@Override public Optional<ProductRef> find(String tenantId, String productId) {
			Boolean archived = products.get(tenantId + "/" + productId);
			return archived == null ? Optional.empty() : Optional.of(new ProductRef(productId, tenantId, archived));
		}
	}

	private InMemoryIngestionTokenRepository tokens;
	private InMemoryTokenAuditRepository audit;
	private FakeCatalog catalog;
	private MutableClock clock;
	private IngestionTokenService service;

	@BeforeEach
	void setUp() {
		tokens = new InMemoryIngestionTokenRepository();
		audit = new InMemoryTokenAuditRepository();
		catalog = new FakeCatalog();
		catalog.products.put(TENANT + "/" + PRODUCT, false);
		clock = new MutableClock();
		service = new IngestionTokenService(tokens, audit, catalog, new TokenSecrets(), clock);
	}

	private static void assertStatus(int expected, Executable call) {
		ResponseStatusException error = assertThrows(ResponseStatusException.class, call);
		assertEquals(expected, error.getStatusCode().value(), error.getReason());
	}

	// US1 — gerar

	@Test
	void createShowsTheSecretOnceAndStoresOnlyItsHash() {
		CreatedToken created = service.create(ADMIN, PRODUCT, "  cluster-producao  ");

		assertTrue(created.secret().startsWith("llt_"));
		IngestionToken stored = tokens.all().getFirst();
		assertEquals(TokenSecrets.sha256Hex(created.secret()), stored.tokenHash());
		assertNotEquals(created.secret(), stored.tokenHash());
		assertEquals("cluster-producao", stored.name());
		assertEquals(IngestionToken.Status.ACTIVE, stored.status());
		assertEquals("admin_1", stored.createdBy());
		assertEquals(TENANT, stored.tenantId());
		assertTrue(stored.usableAt(clock.instant()));
		assertNull(stored.revokedAt());
		assertNull(stored.expiresAt());
	}

	@Test
	void createIsAudited() {
		CreatedToken created = service.create(ADMIN, PRODUCT, "cluster");
		TokenAuditEntry entry = audit.all().getFirst();
		assertEquals(Action.CREATED, entry.action());
		assertEquals(created.token().id(), entry.tokenId());
		assertEquals("admin_1", entry.actorUserId());
		assertEquals(PRODUCT, entry.productId());
	}

	@Test
	void listNeverExposesTheSecret() {
		CreatedToken created = service.create(ADMIN, PRODUCT, "cluster");
		List<IngestionToken> listed = service.list(ADMIN, PRODUCT);
		assertEquals(1, listed.size());
		assertFalse(listed.getFirst().toString().contains(created.secret()));
	}

	@Test
	void onlyAdministratorsManageTokens() {
		CreatedToken created = service.create(ADMIN, PRODUCT, "cluster");
		String id = created.token().id();
		assertStatus(403, () -> service.list(MEMBER, PRODUCT));
		assertStatus(403, () -> service.create(MEMBER, PRODUCT, "outro"));
		assertStatus(403, () -> service.revoke(MEMBER, PRODUCT, id));
		assertStatus(403, () -> service.rotate(MEMBER, PRODUCT, id, null, null));
		assertStatus(403, () -> service.audit(MEMBER, PRODUCT));
		assertEquals(1, tokens.all().size());
	}

	@Test
	void productOfAnotherCompanyIsNotFound() {
		CompanySession otherAdmin = new CompanySession("org_b", "admin_b", true);
		assertStatus(404, () -> service.create(otherAdmin, PRODUCT, "cluster"));
		assertStatus(404, () -> service.list(otherAdmin, PRODUCT));
		assertStatus(404, () -> service.create(ADMIN, "missing", "cluster"));
	}

	@Test
	void archivedProductRefusesNewTokens() {
		catalog.products.put(TENANT + "/" + PRODUCT, true);
		assertStatus(409, () -> service.create(ADMIN, PRODUCT, "cluster"));
		assertTrue(tokens.all().isEmpty());
		assertTrue(audit.all().isEmpty());
	}

	@Test
	void nameMustHaveOneToHundredCharacters() {
		assertStatus(400, () -> service.create(ADMIN, PRODUCT, "   "));
		assertStatus(400, () -> service.create(ADMIN, PRODUCT, null));
		assertStatus(400, () -> service.create(ADMIN, PRODUCT, "a".repeat(101)));
		assertEquals("a".repeat(100), service.create(ADMIN, PRODUCT, "a".repeat(100)).token().name());
	}

	// US2 — revogar

	@Test
	void revokeMarksDateAndAuthorAndIsAudited() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		clock.advance(Duration.ofMinutes(5));

		IngestionToken revoked = service.revoke(ADMIN, PRODUCT, id);

		assertEquals(IngestionToken.Status.REVOKED, revoked.status());
		assertEquals(clock.instant(), revoked.revokedAt());
		assertEquals("admin_1", revoked.revokedBy());
		assertFalse(revoked.usableAt(clock.instant()));
		assertEquals(State.REVOKED, revoked.stateAt(clock.instant()));
		assertEquals(Action.REVOKED, audit.list(TENANT, PRODUCT).getFirst().action());
	}

	@Test
	void revokingTwiceChangesNothingAndIsNotAuditedAgain() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		IngestionToken first = service.revoke(ADMIN, PRODUCT, id);
		clock.advance(Duration.ofMinutes(1));
		IngestionToken second = service.revoke(ADMIN, PRODUCT, id);

		assertEquals(first, second);
		assertEquals(1, tokens.updates());
		assertEquals(2, audit.all().size());
	}

	@Test
	void archivedProductStillAllowsRevocation() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		catalog.products.put(TENANT + "/" + PRODUCT, true);
		assertEquals(IngestionToken.Status.REVOKED, service.revoke(ADMIN, PRODUCT, id).status());
	}

	@Test
	void tokenOfAnotherProductIsNotFound() {
		catalog.products.put(TENANT + "/product-2", false);
		String id = service.create(ADMIN, "product-2", "cluster").token().id();
		assertStatus(404, () -> service.revoke(ADMIN, PRODUCT, id));
		assertStatus(404, () -> service.revoke(ADMIN, PRODUCT, "missing"));
	}

	// US3 — rotacionar

	@Test
	void rotationKeepsBothTokensValidUntilTheEndOfTheTransition() {
		CreatedToken original = service.create(ADMIN, PRODUCT, "cluster");
		clock.advance(Duration.ofMinutes(10));
		Instant rotatedAt = clock.instant();

		Rotation rotation = service.rotate(ADMIN, PRODUCT, original.token().id(), null, 1);

		IngestionToken previous = rotation.previous();
		IngestionToken next = rotation.created().token();
		assertEquals(rotatedAt.plus(Duration.ofHours(1)), previous.expiresAt());
		assertEquals(next.id(), previous.replacedBy());
		assertEquals("cluster", next.name());
		assertNotEquals(original.secret(), rotation.created().secret());
		assertEquals(TokenSecrets.sha256Hex(rotation.created().secret()), next.tokenHash());

		clock.advance(Duration.ofMinutes(59));
		assertTrue(previous.usableAt(clock.instant()));
		assertTrue(next.usableAt(clock.instant()));
		assertEquals(State.EXPIRING, previous.stateAt(clock.instant()));

		clock.advance(Duration.ofMinutes(1));
		assertFalse(previous.usableAt(clock.instant()));
		assertTrue(next.usableAt(clock.instant()));
		assertEquals(State.EXPIRED, previous.stateAt(clock.instant()));
	}

	@Test
	void defaultTransitionIsTwentyFourHoursAndNameCanChange() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		Rotation rotation = service.rotate(ADMIN, PRODUCT, id, " cluster-v2 ", null);
		assertEquals(clock.instant().plus(Duration.ofHours(24)), rotation.previous().expiresAt());
		assertEquals("cluster-v2", rotation.created().token().name());
	}

	@Test
	void rotationIsAuditedWithTheNewTokenAndTheEndOfTheTransition() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		Rotation rotation = service.rotate(ADMIN, PRODUCT, id, null, 2);

		TokenAuditEntry rotated = audit.all().stream().filter(e -> e.action() == Action.ROTATED).findFirst().orElseThrow();
		assertEquals(id, rotated.tokenId());
		assertEquals(rotation.created().token().id(), rotated.relatedTokenId());
		assertEquals(rotation.previous().expiresAt(), rotated.transitionEndsAt());
		assertEquals(2, audit.all().stream().filter(e -> e.action() == Action.CREATED).count());
	}

	@Test
	void onlyAnActiveTokenOutsideATransitionCanBeRotated() {
		String revoked = service.create(ADMIN, PRODUCT, "revogado").token().id();
		service.revoke(ADMIN, PRODUCT, revoked);
		assertStatus(409, () -> service.rotate(ADMIN, PRODUCT, revoked, null, null));

		String rotating = service.create(ADMIN, PRODUCT, "em-transicao").token().id();
		service.rotate(ADMIN, PRODUCT, rotating, null, 1);
		assertStatus(409, () -> service.rotate(ADMIN, PRODUCT, rotating, null, null));

		clock.advance(Duration.ofHours(2));
		assertStatus(409, () -> service.rotate(ADMIN, PRODUCT, rotating, null, null));
	}

	@Test
	void transitionMustBeBetweenOneAndOneHundredSixtyEightHours() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		assertStatus(400, () -> service.rotate(ADMIN, PRODUCT, id, null, 0));
		assertStatus(400, () -> service.rotate(ADMIN, PRODUCT, id, null, 169));
		assertNull(tokens.find(TENANT, PRODUCT, id).orElseThrow().expiresAt());
		assertEquals(1, tokens.all().size());
		service.rotate(ADMIN, PRODUCT, id, null, 168);
	}

	@Test
	void archivedProductRefusesRotation() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		catalog.products.put(TENANT + "/" + PRODUCT, true);
		assertStatus(409, () -> service.rotate(ADMIN, PRODUCT, id, null, null));
		assertEquals(1, tokens.all().size());
	}

	@Test
	void revokingATokenInTransitionStopsItAtOnce() {
		String id = service.create(ADMIN, PRODUCT, "cluster").token().id();
		service.rotate(ADMIN, PRODUCT, id, null, 24);
		IngestionToken revoked = service.revoke(ADMIN, PRODUCT, id);
		assertEquals(State.REVOKED, revoked.stateAt(clock.instant()));
		assertFalse(revoked.usableAt(clock.instant()));
	}

	// US4 — auditoria

	@Test
	void auditIsNewestFirstAndNeverHoldsTheTokenOrItsHash() {
		CreatedToken created = service.create(ADMIN, PRODUCT, "cluster");
		clock.advance(Duration.ofMinutes(1));
		Rotation rotation = service.rotate(ADMIN, PRODUCT, created.token().id(), null, 1);
		clock.advance(Duration.ofMinutes(1));
		service.revoke(ADMIN, PRODUCT, created.token().id());

		List<TokenAuditEntry> entries = service.audit(ADMIN, PRODUCT);
		assertEquals(4, entries.size());
		assertEquals(Action.REVOKED, entries.getFirst().action());
		assertEquals(Action.CREATED, entries.getLast().action());
		for (TokenAuditEntry entry : entries) {
			String text = entry.toString();
			assertFalse(text.contains(created.secret()));
			assertFalse(text.contains(rotation.created().secret()));
			assertFalse(text.contains(created.token().tokenHash()));
		}
	}
}
