package br.com.lhamalog.core.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.lhamalog.core.tenant.adapter.out.mongo.TenantDocument;

/**
 * Real Clerk session tokens ({@link TestJwks}) for HTTP tests, plus an ACTIVE tenant for each organization used, since
 * the security layer only grants requests of an existing tenant (LLT-163).
 */
public final class TestTenants {

	private final MongoTemplate mongo;

	private final Map<String, String> tenantIds = new ConcurrentHashMap<>();

	public TestTenants(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/** Internal tenant id of the organization, creating an ACTIVE tenant on first use. */
	public String tenantId(String organization) {
		return tenantIds.computeIfAbsent(organization, (org) -> {
			String tenantId = UUID.randomUUID().toString();
			mongo.insert(new TenantDocument(tenantId, org, "Tenant de teste", "ACTIVE"));
			return tenantId;
		});
	}

	/** Session token v2 ({@code o} claim) of the user in the organization. */
	public RequestPostProcessor session(String organization, String user, String role) {
		tenantId(organization);
		return bearer(TestJwks.token().organization(organization, role).subject(user));
	}

	/** Session token v1: flat {@code org_id}/{@code org_role} and no {@code sts}. The core rejects it with 401. */
	public RequestPostProcessor legacySession(String organization, String user, String role) {
		tenantId(organization);
		return bearer(TestJwks.token()
			.without("o")
			.without("sts")
			.claim("org_id", organization)
			.claim("org_role", role)
			.subject(user));
	}

	/** Valid token without an active organization: denied with 403. */
	public static RequestPostProcessor withoutOrganization(String user) {
		return bearer(TestJwks.token().without("o").subject(user));
	}

	private static RequestPostProcessor bearer(TestJwks.Token token) {
		String header = token.bearer();
		return (request) -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, header);
			return request;
		};
	}

}
