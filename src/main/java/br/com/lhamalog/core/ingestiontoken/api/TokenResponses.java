package br.com.lhamalog.core.ingestiontoken.api;

import java.time.Instant;
import br.com.lhamalog.core.ingestiontoken.application.IngestionToken;
import br.com.lhamalog.core.ingestiontoken.application.IngestionTokenService;
import br.com.lhamalog.core.ingestiontoken.application.TokenAuditEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the token API. No response carries the token hash. */
public final class TokenResponses {
	public static final String SHOWN_ONCE = "Copie o token agora: ele não será exibido de novo.";

	private TokenResponses() {}

	public record CreateTokenRequest(@NotBlank @Size(max = IngestionTokenService.NAME_MAX) String name) {}

	public record RotateTokenRequest(@Size(max = IngestionTokenService.NAME_MAX) String name,
			@Min(IngestionTokenService.MIN_TRANSITION_HOURS) @Max(IngestionTokenService.MAX_TRANSITION_HOURS)
			Integer transitionHours) {}

	public record TokenView(String id, String name, IngestionToken.State state, Instant createdAt, String createdBy,
			Instant expiresAt, Instant revokedAt, String revokedBy, String replacedBy) {
		static TokenView of(IngestionToken token, Instant now) {
			return new TokenView(token.id(), token.name(), token.stateAt(now), token.createdAt(), token.createdBy(),
					token.expiresAt(), token.revokedAt(), token.revokedBy(), token.replacedBy());
		}
	}

	/** The only response that carries the token value. */
	public record CreatedTokenView(TokenView token, String secret, String notice) {
		static CreatedTokenView of(IngestionTokenService.CreatedToken created, Instant now) {
			return new CreatedTokenView(TokenView.of(created.token(), now), created.secret(), SHOWN_ONCE);
		}
	}

	public record RotationView(CreatedTokenView created, TokenView previous) {
		static RotationView of(IngestionTokenService.Rotation rotation, Instant now) {
			return new RotationView(CreatedTokenView.of(rotation.created(), now), TokenView.of(rotation.previous(), now));
		}
	}

	public record AuditView(String id, String tokenId, TokenAuditEntry.Action action, String actorUserId, Instant at,
			String relatedTokenId, Instant transitionEndsAt) {
		static AuditView of(TokenAuditEntry entry) {
			return new AuditView(entry.id(), entry.tokenId(), entry.action(), entry.actorUserId(), entry.at(),
					entry.relatedTokenId(), entry.transitionEndsAt());
		}
	}
}
