package br.com.lhamalog.core.company;

import java.time.Instant;
import java.util.List;

public final class CompanyContextViews {
	private CompanyContextViews() {}

	public record ContextBody(String sector, List<String> environments, List<String> monitoredSystems,
			String criticalWindows, String description) {
		static ContextBody from(CompanyContextSnapshot context) {
			return new ContextBody(context.sector(), context.environments(), context.monitoredSystems(),
					context.criticalWindows(), context.description());
		}
	}

	public record Revision(long revision, Instant createdAt, String actorUserId, ContextBody context) {
		static Revision from(CompanyContextRevision stored) {
			return new Revision(stored.revision(), stored.createdAt(), stored.actorUserId(),
					ContextBody.from(stored.context()));
		}
	}

	public record RevisionList(List<Revision> revisions) {
		static RevisionList from(List<CompanyContextRevision> stored) {
			return new RevisionList(stored.stream().map(Revision::from).toList());
		}
	}
}
