package br.com.lhamalog.core.shared;

import br.com.lhamalog.core.identity.domain.OrganizationRole;
import br.com.lhamalog.core.identity.domain.RequestContext;

/**
 * Company-scoped view of the current request used by Empresa and Produto. Built from the {@link RequestContext}
 * resolved by the security layer (LLT-163), so tenant and role were already validated: active organization, known
 * role and an ACTIVE tenant.
 *
 * @param tenantId internal tenant id (tenant collection), never the Clerk organization id
 * @param userId Clerk user id ({@code sub})
 * @param administrator whether the Clerk organization role is admin
 */
public record CompanySession(String tenantId, String userId, boolean administrator) {

	public static CompanySession from(RequestContext context) {
		return new CompanySession(context.tenantId(), context.clerkUserId(),
				context.role() == OrganizationRole.ADMIN);
	}

}
