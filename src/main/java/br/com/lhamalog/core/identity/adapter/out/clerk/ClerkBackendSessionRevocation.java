package br.com.lhamalog.core.identity.adapter.out.clerk;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.identity.application.port.out.SessionRevocation;
import tools.jackson.databind.JsonNode;

/**
 * Revokes sessions through the Clerk Backend API (ADR_032): lists the user's active sessions, then revokes each one.
 * Uses Spring's {@link RestClient}; no Clerk SDK.
 */
public class ClerkBackendSessionRevocation implements SessionRevocation {

	/** Backend API page size limit is "greater than zero and less than 501". */
	static final int PAGE_LIMIT = 500;

	private static final Logger log = LoggerFactory.getLogger(ClerkBackendSessionRevocation.class);

	private final RestClient restClient;

	private final String secretKey;

	public ClerkBackendSessionRevocation(RestClient.Builder builder, String apiUrl, String secretKey,
			Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = builder.clone().baseUrl(apiUrl).requestFactory(requestFactory).build();
		this.secretKey = secretKey;
	}

	@Override
	public void revokeAllActiveSessions(String clerkUserId) {
		if (secretKey == null || secretKey.isBlank()) {
			log.warn("CLERK_SECRET_KEY is not configured; skipping session revocation for clerkUserId={}",
					clerkUserId);
			return;
		}
		List<String> sessionIds = listActiveSessionIds(clerkUserId);
		for (String sessionId : sessionIds) {
			restClient.post()
				.uri("/v1/sessions/{sessionId}/revoke", sessionId)
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.retrieve()
				.toBodilessEntity();
		}
		log.info("Revoked {} Clerk session(s) for clerkUserId={}", sessionIds.size(), clerkUserId);
	}

	private List<String> listActiveSessionIds(String clerkUserId) {
		JsonNode body = restClient.get()
			.uri((uri) -> uri.path("/v1/sessions")
				.queryParam("user_id", clerkUserId)
				.queryParam("status", "active")
				.queryParam("limit", PAGE_LIMIT)
				.build())
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.retrieve()
			.body(JsonNode.class);
		// The Backend API answers a plain array, or {"data": [...]} when pagination metadata is requested.
		JsonNode sessions = (body != null && body.isObject()) ? body.get("data") : body;
		List<String> ids = new ArrayList<>();
		if (sessions != null && sessions.isArray()) {
			for (JsonNode session : sessions) {
				JsonNode id = session.get("id");
				if (id != null && id.isString()) {
					ids.add(id.asString());
				}
			}
		}
		return ids;
	}

	private String bearer() {
		return "Bearer " + secretKey;
	}

}
