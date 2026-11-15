package br.com.lhamalog.core.member;

import java.time.Instant;
import java.util.List;

public record CompanyMemberView(String userId, String status, Instant lastAccessAt) {
	public record ListResponse(List<CompanyMemberView> members) {}

	static CompanyMemberView from(CompanyMember member) {
		return new CompanyMemberView(member.userId(), member.status(), member.lastAccessAt());
	}
}
