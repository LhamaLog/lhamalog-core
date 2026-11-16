package br.com.lhamalog.core.usage.application;

/**
 * Commercial plan tiers and default raw-log retention days from ADR_131
 * {@code TabelaPrecificacao.retencaoDias}.
 */
public enum PlanTier {
	FREE(7),
	STARTER(15),
	PRO(30),
	BUSINESS(90),
	ENTERPRISE(90);

	private final int retentionDays;

	PlanTier(int retentionDays) {
		this.retentionDays = retentionDays;
	}

	/**
	 * ADR_131 default retention in days for this tier ({@code retencaoDias}).
	 */
	public int retentionDays() {
		return retentionDays;
	}

	/**
	 * Parses a stored tier name case-insensitively. Blank or unknown values yield {@code null}
	 * so legacy documents remain readable.
	 */
	public static PlanTier parseLenient(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String normalized = raw.trim();
		for (PlanTier tier : values()) {
			if (tier.name().equalsIgnoreCase(normalized)) {
				return tier;
			}
		}
		return null;
	}
}
