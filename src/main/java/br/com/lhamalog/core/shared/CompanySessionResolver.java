package br.com.lhamalog.core.shared;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CompanySessionResolver {
	private final Set<String> administratorRoles;

	public CompanySessionResolver(@Value("${clerk.administrator-roles:admin}") String roles) {
		administratorRoles = Arrays.stream(roles.split(",")).map(String::trim)
				.filter(role -> !role.isBlank()).map(role -> role.replaceFirst("^org:", ""))
				.collect(Collectors.toUnmodifiableSet());
	}

	public CompanySession resolve(Jwt jwt) { return CompanySession.from(jwt, administratorRoles); }
}
