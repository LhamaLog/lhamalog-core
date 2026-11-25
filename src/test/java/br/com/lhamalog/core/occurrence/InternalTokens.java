package br.com.lhamalog.core.occurrence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Internal JWTs for the Occurrence Module tests (spec 013). The secrets are the fictitious ones of
 * {@code src/test/resources/config/application.properties}; nothing here is a real credential.
 */
final class InternalTokens {

	static final String CURRENT_SECRET = "segredo-ficticio-de-teste-do-modulo-ocorrencia-1";

	static final String PREVIOUS_SECRET = "segredo-ficticio-de-teste-do-modulo-ocorrencia-2";

	static final String OTHER_SECRET = "segredo-ficticio-que-o-core-nao-conhece-000000";

	private String secret = CURRENT_SECRET;

	private JWSAlgorithm algorithm = JWSAlgorithm.HS256;

	private final JWTClaimsSet.Builder claims;

	private InternalTokens() {
		Instant now = Instant.now();
		claims = new JWTClaimsSet.Builder().issuer("lhamalog-internal")
			.audience(List.of("lhamalog-modulo-ocorrencia"))
			.subject("lhamalog-finder")
			.issueTime(Date.from(now))
			.notBeforeTime(Date.from(now.minusSeconds(1)))
			.expirationTime(Date.from(now.plusSeconds(300)));
	}

	static InternalTokens finder() {
		return new InternalTokens();
	}

	static String bearer() {
		return "Bearer " + finder().sign();
	}

	InternalTokens secret(String value) {
		this.secret = value;
		return this;
	}

	InternalTokens algorithm(JWSAlgorithm value) {
		this.algorithm = value;
		return this;
	}

	InternalTokens claim(String name, Object value) {
		claims.claim(name, value instanceof Instant instant ? Date.from(instant) : value);
		return this;
	}

	String sign() {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims.build());
			byte[] key = secret.getBytes(StandardCharsets.UTF_8);
			if (algorithm == JWSAlgorithm.HS512 && key.length < 64) key = (secret + secret).getBytes(StandardCharsets.UTF_8);
			jwt.sign(new MACSigner(key));
			return jwt.serialize();
		}
		catch (JOSEException failure) {
			throw new IllegalStateException(failure);
		}
	}

}
