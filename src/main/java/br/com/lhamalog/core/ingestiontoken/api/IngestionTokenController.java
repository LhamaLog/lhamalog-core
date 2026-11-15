package br.com.lhamalog.core.ingestiontoken.api;

import java.time.Instant;
import java.util.List;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.AuditView;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.CreateTokenRequest;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.CreatedTokenView;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.RotateTokenRequest;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.RotationView;
import br.com.lhamalog.core.ingestiontoken.api.TokenResponses.TokenView;
import br.com.lhamalog.core.ingestiontoken.application.IngestionTokenService;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Ingestion tokens of a product (feature 005). Company administrators only. */
@RestController
@RequestMapping("/api/products/{productId}/tokens")
public class IngestionTokenController {
	private final IngestionTokenService service;
	private final CompanySessionResolver sessions;

	public IngestionTokenController(IngestionTokenService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@GetMapping
	public List<TokenView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId) {
		Instant now = service.now();
		return service.list(sessions.resolve(jwt), productId).stream().map(token -> TokenView.of(token, now)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedTokenView create(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId,
			@Valid @RequestBody CreateTokenRequest request) {
		return CreatedTokenView.of(service.create(sessions.resolve(jwt), productId, request.name()), service.now());
	}

	@PostMapping("/{tokenId}/revoke")
	public TokenView revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId,
			@PathVariable String tokenId) {
		return TokenView.of(service.revoke(sessions.resolve(jwt), productId, tokenId), service.now());
	}

	@PostMapping("/{tokenId}/rotate")
	@ResponseStatus(HttpStatus.CREATED)
	public RotationView rotate(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId,
			@PathVariable String tokenId, @Valid @RequestBody(required = false) RotateTokenRequest request) {
		RotateTokenRequest body = request == null ? new RotateTokenRequest(null, null) : request;
		return RotationView.of(service.rotate(sessions.resolve(jwt), productId, tokenId, body.name(),
				body.transitionHours()), service.now());
	}

	@GetMapping("/audit")
	public List<AuditView> audit(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId) {
		return service.audit(sessions.resolve(jwt), productId).stream().map(AuditView::of).toList();
	}
}
