package br.com.lhamalog.core.ingestiontoken.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import br.com.lhamalog.core.ingestiontoken.application.ProductCatalog.ProductRef;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditEntry.Action;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rules for the ingestion tokens of a product (feature 010): list, create, revoke, rotate and audit. Only company
 * administrators (DONO/ADMIN) may use them (ADR_109). The token value leaves this class only inside
 * {@link CreatedToken}; it is never stored, audited or logged, and neither is its hash.
 */
@Service
public class IngestionTokenService {
	private static final Logger log = LoggerFactory.getLogger(IngestionTokenService.class);

	/** Business 403 code: the Web keeps the session for any 403 that carries a code (spec 008, FR-010). */
	static final String NOT_ADMIN_CODE = "not_admin";

	public static final int NAME_MAX = 100;
	public static final int DEFAULT_TRANSITION_HOURS = 24;
	public static final int MIN_TRANSITION_HOURS = 1;
	public static final int MAX_TRANSITION_HOURS = 168;

	static final String NOT_ADMIN = "Somente donos e administradores podem gerenciar tokens.";
	static final String PRODUCT_NOT_FOUND = "Produto não encontrado ou sem acesso.";
	static final String TOKEN_NOT_FOUND = "Token não encontrado.";
	static final String INVALID_NAME = "Informe um nome de 1 a 100 caracteres para o token.";
	static final String INVALID_TRANSITION = "O período de transição deve ficar entre 1 e 168 horas.";
	static final String ARCHIVED_PRODUCT = "Produto arquivado não pode receber tokens novos.";
	static final String NOT_USABLE = "Só um token ativo pode ser rotacionado.";
	static final String ALREADY_ROTATING = "Este token já está em período de transição.";

	private final IngestionTokenRepository tokens;
	private final TokenAuditRepository audit;
	private final ProductCatalog products;
	private final TokenSecrets secrets;
	private final InstantSource clock;

	@Autowired
	public IngestionTokenService(IngestionTokenRepository tokens, TokenAuditRepository audit, ProductCatalog products,
			TokenSecrets secrets) {
		this(tokens, audit, products, secrets, InstantSource.system());
	}

	IngestionTokenService(IngestionTokenRepository tokens, TokenAuditRepository audit, ProductCatalog products,
			TokenSecrets secrets, InstantSource clock) {
		this.tokens = tokens;
		this.audit = audit;
		this.products = products;
		this.secrets = secrets;
		this.clock = clock;
	}

	/** A token just created: the only place where its value exists. */
	public record CreatedToken(IngestionToken token, String secret) {}

	/** Result of a rotation: the new token (with its value) and the old one, now expiring. */
	public record Rotation(CreatedToken created, IngestionToken previous) {}

	public Instant now() {
		// MongoDB keeps milliseconds; truncating keeps responses equal to what is stored.
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

	public List<IngestionToken> list(CompanySession session, String productId) {
		ProductRef product = requireProduct(session, productId);
		return tokens.list(product.tenantId(), product.id());
	}

	public CreatedToken create(CompanySession session, String productId, String name) {
		ProductRef product = requireProduct(session, productId);
		String cleanName = requireName(name);
		if (product.archived()) throw conflict(ARCHIVED_PRODUCT);
		CreatedToken created = issue(product, cleanName, session.userId(), now());
		audit.append(entry(created.token(), Action.CREATED, session.userId(), created.token().createdAt(), null, null));
		log.info("event=ingestion_token.created | Ingestion token created | tenantId={} productId={} tokenId={}",
				product.tenantId(), product.id(), created.token().id());
		return created;
	}

	/** Revoking an already revoked token changes nothing and is not audited again. Archived products allowed. */
	public IngestionToken revoke(CompanySession session, String productId, String tokenId) {
		ProductRef product = requireProduct(session, productId);
		IngestionToken current = requireToken(product, tokenId);
		if (current.status() == IngestionToken.Status.REVOKED) return current;
		Instant at = now();
		IngestionToken revoked = tokens.update(current.revoke(at, session.userId()));
		audit.append(entry(revoked, Action.REVOKED, session.userId(), at, null, null));
		log.info("event=ingestion_token.revoked | Ingestion token revoked; the Collector stops accepting it within its cache time | tenantId={} productId={} tokenId={}",
				product.tenantId(), product.id(), revoked.id());
		return revoked;
	}

	/**
	 * Creates a new token and makes the old one expire after the transition, so both are accepted meanwhile.
	 * The new token is written first: if a later step fails, the product still has a valid token.
	 */
	public Rotation rotate(CompanySession session, String productId, String tokenId, String name,
			Integer transitionHours) {
		ProductRef product = requireProduct(session, productId);
		Duration transition = requireTransition(transitionHours);
		String requestedName = name == null || name.isBlank() ? null : requireName(name);
		IngestionToken current = requireToken(product, tokenId);
		if (product.archived()) throw conflict(ARCHIVED_PRODUCT);
		Instant at = now();
		if (!current.usableAt(at)) throw conflict(NOT_USABLE);
		if (current.expiresAt() != null) throw conflict(ALREADY_ROTATING);

		CreatedToken created = issue(product, requestedName == null ? current.name() : requestedName, session.userId(), at);
		Instant transitionEndsAt = at.plus(transition);
		IngestionToken previous = tokens.update(current.rotateOut(transitionEndsAt, created.token().id()));
		audit.append(entry(previous, Action.ROTATED, session.userId(), at, created.token().id(), transitionEndsAt));
		audit.append(entry(created.token(), Action.CREATED, session.userId(), at, null, null));
		log.info("event=ingestion_token.rotated | Ingestion token rotated; both tokens valid until the transition ends | tenantId={} productId={} tokenId={} newTokenId={} transitionEndsAt={}",
				product.tenantId(), product.id(), previous.id(), created.token().id(), transitionEndsAt);
		return new Rotation(created, previous);
	}

	public List<TokenAuditEntry> audit(CompanySession session, String productId) {
		ProductRef product = requireProduct(session, productId);
		return audit.list(product.tenantId(), product.id());
	}

	private CreatedToken issue(ProductRef product, String name, String userId, Instant at) {
		String secret = secrets.generate();
		IngestionToken token = tokens.insert(IngestionToken.issue(UUID.randomUUID().toString(), product.tenantId(),
				product.id(), name, TokenSecrets.sha256Hex(secret), at, userId));
		return new CreatedToken(token, secret);
	}

	private ProductRef requireProduct(CompanySession session, String productId) {
		if (!session.administrator()) throw new ApiStatusException(HttpStatus.FORBIDDEN, NOT_ADMIN, NOT_ADMIN_CODE);
		return products.find(session.tenantId(), productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND));
	}

	private IngestionToken requireToken(ProductRef product, String tokenId) {
		return tokens.find(product.tenantId(), product.id(), tokenId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, TOKEN_NOT_FOUND));
	}

	private static String requireName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty() || trimmed.length() > NAME_MAX) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_NAME);
		}
		return trimmed;
	}

	private static Duration requireTransition(Integer hours) {
		int value = hours == null ? DEFAULT_TRANSITION_HOURS : hours;
		if (value < MIN_TRANSITION_HOURS || value > MAX_TRANSITION_HOURS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TRANSITION);
		}
		return Duration.ofHours(value);
	}

	private static TokenAuditEntry entry(IngestionToken token, Action action, String actor, Instant at,
			String relatedTokenId, Instant transitionEndsAt) {
		return new TokenAuditEntry(UUID.randomUUID().toString(), token.tenantId(), token.productId(), token.id(), action,
				actor, at, relatedTokenId, transitionEndsAt);
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}
}
