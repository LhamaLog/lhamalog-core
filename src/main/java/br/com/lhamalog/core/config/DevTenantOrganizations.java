package br.com.lhamalog.core.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clerk organizations configured for local development tenants ({@code DEV_TENANT_CLERK_ORG_ID}, spec 007): a comma
 * separated list. Items are stripped; empty items and repeats are ignored; values that are not Clerk organization ids
 * are kept apart so the caller can warn about them. Order is preserved.
 *
 * @param valid organization ids to provision, in configuration order
 * @param discarded values that are not organization ids, in configuration order
 */
public record DevTenantOrganizations(List<String> valid, List<String> discarded) {

	private static final Pattern ORGANIZATION_ID = Pattern.compile("^org_[A-Za-z0-9]+$");

	public static DevTenantOrganizations parse(String raw) {
		Set<String> valid = new LinkedHashSet<>();
		Set<String> discarded = new LinkedHashSet<>();
		if (raw != null) {
			for (String item : raw.split(",")) {
				String value = item.strip();
				if (value.isEmpty()) {
					continue;
				}
				(ORGANIZATION_ID.matcher(value).matches() ? valid : discarded).add(value);
			}
		}
		return new DevTenantOrganizations(List.copyOf(valid), List.copyOf(discarded));
	}

}
