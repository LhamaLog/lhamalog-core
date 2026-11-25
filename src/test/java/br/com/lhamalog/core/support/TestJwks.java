package br.com.lhamalog.core.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Test-only Clerk instance: an in-memory RSA key pair whose public JWK is served by the JDK HTTP server, plus a
 * builder of session tokens. Nothing talks to the real Clerk.
 */
public final class TestJwks {

	public static final String ISSUER = "https://clerk.test.invalid";

	public static final String AUTHORIZED_PARTY = "http://localhost:5173";

	private static final RSAKey SIGNING_KEY = generate("test-key");

	/** Same key id, different key material: signatures made with it must be rejected. */
	private static final RSAKey FOREIGN_KEY = generate("test-key");

	/** Key id the JWKS never publishes: the decoder has to fetch the JWKS again to look for it. */
	private static final RSAKey UNPUBLISHED_KEY = generate("rotated-key");

	/** HMAC secret for tokens with a symmetric algorithm, which must be rejected (RS256 only). */
	private static final byte[] HMAC_SECRET = "test-only-hmac-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

	private static final AtomicInteger JWKS_REQUESTS = new AtomicInteger();

	private static volatile boolean unavailable;

	private static HttpServer server;

	private TestJwks() {
	}

	public static synchronized String jwksUri() {
		if (server == null) {
			server = startServer();
		}
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
	}

	public static Token token() {
		return new Token();
	}

	/** Makes the JWKS endpoint answer 503, as if the Clerk instance were down. */
	public static void setUnavailable(boolean value) {
		unavailable = value;
	}

	/** Number of requests the JWKS endpoint received so far. */
	public static int jwksRequests() {
		return JWKS_REQUESTS.get();
	}

	private static RSAKey generate(String keyId) {
		try {
			return new RSAKeyGenerator(2048).keyID(keyId).generate();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static HttpServer startServer() {
		try {
			HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			httpServer.createContext("/.well-known/jwks.json", (exchange) -> {
				JWKS_REQUESTS.incrementAndGet();
				if (unavailable) {
					exchange.sendResponseHeaders(503, -1);
					exchange.close();
					return;
				}
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, jwks.length);
				try (OutputStream body = exchange.getResponseBody()) {
					body.write(jwks);
				}
			});
			httpServer.setExecutor(null);
			httpServer.start();
			return httpServer;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Builder of a session token that is valid by default (v2 claims, active session, organization). */
	public static final class Token {

		private final Map<String, Object> claims = new HashMap<>();

		private RSAKey key = SIGNING_KEY;

		private boolean hmac;

		private Token() {
			Instant now = Instant.now();
			claims.put("iss", ISSUER);
			claims.put("azp", AUTHORIZED_PARTY);
			claims.put("sub", "user_test");
			claims.put("sid", "sess_test");
			claims.put("sts", "active");
			claims.put("v", 2);
			claims.put("iat", Date.from(now));
			claims.put("nbf", Date.from(now.minusSeconds(1)));
			claims.put("exp", Date.from(now.plusSeconds(60)));
			organization("org_test", "admin");
		}

		public Token claim(String name, Object value) {
			claims.put(name, (value instanceof Instant instant) ? Date.from(instant) : value);
			return this;
		}

		public Token without(String name) {
			claims.remove(name);
			return this;
		}

		public Token organization(String id, String role) {
			Map<String, Object> organization = new HashMap<>();
			organization.put("id", id);
			if (role != null) {
				organization.put("rol", role);
			}
			organization.put("slg", "test-org");
			organization.put("per", "");
			claims.put("o", organization);
			return this;
		}

		public Token subject(String subject) {
			return claim("sub", subject);
		}

		public Token signedWithForeignKey() {
			this.key = FOREIGN_KEY;
			return this;
		}

		/** Signs with a key id that is not in the JWKS. */
		public Token signedWithUnpublishedKey() {
			this.key = UNPUBLISHED_KEY;
			return this;
		}

		/** Signs with HS256 instead of RS256. */
		public Token signedWithHmac() {
			this.hmac = true;
			return this;
		}

		public String encode() {
			JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
			claims.forEach(builder::claim);
			JWSAlgorithm algorithm = hmac ? JWSAlgorithm.HS256 : JWSAlgorithm.RS256;
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(),
					builder.build());
			try {
				jwt.sign(hmac ? new MACSigner(HMAC_SECRET) : new RSASSASigner(key));
			}
			catch (JOSEException ex) {
				throw new IllegalStateException(ex);
			}
			return jwt.serialize();
		}

		public String bearer() {
			return "Bearer " + encode();
		}

	}

}
