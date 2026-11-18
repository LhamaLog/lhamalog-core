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
import org.springframework.web.bind.annotation.*;

/**
 * Ingestion tokens of a product (feature 010). Company administrators only. The session comes from the token already
 * validated by the security layer (LLT-163), so {@code tenantId} is the internal tenant id.
 */
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
	public List<TokenView> list(@PathVariable String productId) {
		Instant now = service.now();
		return service.list(sessions.current(), productId).stream().map(token -> TokenView.of(token, now)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedTokenView create(@PathVariable String productId, @Valid @RequestBody CreateTokenRequest request) {
		return CreatedTokenView.of(service.create(sessions.current(), productId, request.name()), service.now());
	}

	@PostMapping("/{tokenId}/revoke")
	public TokenView revoke(@PathVariable String productId, @PathVariable String tokenId) {
		return TokenView.of(service.revoke(sessions.current(), productId, tokenId), service.now());
	}

	@PostMapping("/{tokenId}/rotate")
	@ResponseStatus(HttpStatus.CREATED)
	public RotationView rotate(@PathVariable String productId, @PathVariable String tokenId,
			@Valid @RequestBody(required = false) RotateTokenRequest request) {
		RotateTokenRequest body = request == null ? new RotateTokenRequest(null, null) : request;
		return RotationView.of(service.rotate(sessions.current(), productId, tokenId, body.name(),
				body.transitionHours()), service.now());
	}

	@GetMapping("/audit")
	public List<AuditView> audit(@PathVariable String productId) {
		return service.audit(sessions.current(), productId).stream().map(AuditView::of).toList();
	}
}
