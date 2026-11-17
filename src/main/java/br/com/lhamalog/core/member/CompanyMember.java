package br.com.lhamalog.core.member;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("CompanyMember")
public record CompanyMember(@Id String id, String tenantId, String userId, boolean administrator, String status,
		Instant lastAccessAt, @Version Long version) {
	public static final String ACTIVE = "active";
	public static final String DEACTIVATED = "deactivated";

	public static String idOf(String tenantId, String userId) {
		return tenantId + "/" + userId;
	}

	public boolean activeAdministrator() {
		return administrator && ACTIVE.equals(status);
	}
}
