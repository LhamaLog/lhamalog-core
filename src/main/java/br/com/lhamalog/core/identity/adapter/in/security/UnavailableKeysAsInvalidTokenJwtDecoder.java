package br.com.lhamalog.core.identity.adapter.in.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Treats a token that cannot be checked as an invalid one. When the Clerk JWKS cannot be fetched, the Nimbus decoder
 * throws a plain {@link JwtException}, which Spring Security turns into a 500; the spec asks for the basic 401
 * instead (LLT-163, edge case "JWKS indisponível"). Keys already fetched stay cached by the delegate.
 */
public final class UnavailableKeysAsInvalidTokenJwtDecoder implements JwtDecoder {

	private static final Logger log = LoggerFactory.getLogger(UnavailableKeysAsInvalidTokenJwtDecoder.class);

	private final JwtDecoder delegate;

	public UnavailableKeysAsInvalidTokenJwtDecoder(JwtDecoder delegate) {
		this.delegate = delegate;
	}

	@Override
	public Jwt decode(String token) {
		try {
			return delegate.decode(token);
		}
		catch (BadJwtException ex) {
			throw ex;
		}
		catch (JwtException ex) {
			// Never log the token; the cause says why the keys could not be read.
			Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
			log.warn("Session token could not be validated: signing keys unavailable ({})",
					cause.getClass().getSimpleName());
			throw new BadJwtException("Signing keys unavailable", ex);
		}
	}

}
