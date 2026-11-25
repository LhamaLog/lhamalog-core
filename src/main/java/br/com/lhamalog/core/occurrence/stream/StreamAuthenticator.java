package br.com.lhamalog.core.occurrence.stream;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import br.com.lhamalog.core.identity.adapter.in.security.ClerkAuthentication;
import br.com.lhamalog.core.identity.adapter.in.security.ClerkAuthenticationConverter;
import br.com.lhamalog.core.identity.domain.AccessDecision;
import br.com.lhamalog.core.member.CompanyMemberService;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.CompanySession;

/**
 * Validates the token of a WebSocket connection exactly like the {@code /api} routes (spec 015, research R2): the
 * Clerk {@link JwtDecoder}, the {@code AccessResolver} through the {@link ClerkAuthenticationConverter} (active
 * organization, known role, ACTIVE tenant) and the deactivated-member check of the {@code MemberAccessInterceptor}.
 */
@Component
public class StreamAuthenticator {

	private final JwtDecoder decoder;

	private final ClerkAuthenticationConverter converter;

	private final CompanyMemberService members;

	private final ProductService products;

	public StreamAuthenticator(JwtDecoder decoder, ClerkAuthenticationConverter converter, CompanyMemberService members,
			ProductService products) {
		this.decoder = decoder;
		this.converter = converter;
		this.members = members;
		this.products = products;
	}

	public sealed interface Result {

		/** Token valid for an active member of an active company. */
		record Accepted(CompanySession session, Instant expiresAt) implements Result {}

		/** 4401: the token itself is not valid. */
		record InvalidToken() implements Result {}

		/** 4403: valid token without access. */
		record Denied() implements Result {}

	}

	public Result authenticate(String token) {
		if (token == null || token.isBlank()) return new Result.InvalidToken();
		Jwt jwt;
		try {
			jwt = decoder.decode(token.strip());
		}
		catch (JwtException invalid) {
			return new Result.InvalidToken();
		}
		if (!(converter.convert(jwt) instanceof ClerkAuthentication clerk)
				|| !(clerk.decision() instanceof AccessDecision.Granted granted)) {
			return new Result.Denied();
		}
		CompanySession session = CompanySession.from(granted.context());
		if (members.isDeactivated(session.tenantId(), session.userId())) return new Result.Denied();
		return new Result.Accepted(session, jwt.getExpiresAt());
	}

	/** Products the session sees, active and archived (same rule as the occurrence detail, spec 012). */
	public Set<String> visibleProducts(CompanySession session) {
		Set<String> ids = new HashSet<>();
		for (Product product : products.list(session, false)) ids.add(product.id());
		for (Product product : products.list(session, true)) ids.add(product.id());
		return ids;
	}

}
