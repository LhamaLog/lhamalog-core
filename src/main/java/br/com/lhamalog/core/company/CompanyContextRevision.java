package br.com.lhamalog.core.company;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("TenantContextRevision")
@CompoundIndex(name = "context_revision_tenant", def = "{'tenantId': 1, 'revision': 1}", unique = true)
public record CompanyContextRevision(@Id String id, String tenantId, long revision, Instant createdAt, String actorUserId,
		String sector, List<String> environments, List<String> monitoredSystems, String criticalWindows, String description) {

	public static CompanyContextRevision of(String tenantId, long revision, Instant createdAt, String actorUserId,
			CompanyContextSnapshot context) {
		return new CompanyContextRevision(tenantId + ":" + revision, tenantId, revision, createdAt, actorUserId,
				context.sector(), context.environments(), context.monitoredSystems(), context.criticalWindows(),
				context.description());
	}

	public CompanyContextSnapshot context() {
		return new CompanyContextSnapshot(sector, environments, monitoredSystems, criticalWindows, description);
	}
}
