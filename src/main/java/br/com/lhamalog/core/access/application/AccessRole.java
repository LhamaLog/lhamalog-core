package br.com.lhamalog.core.access.application;

/**
 * Access role of a person in the company (ADR_122/123): DONO, ADMIN or MEMBRO in the Membro collection. It is what the
 * person may do; notification roles (feature 011, US3) only decide who receives alerts and grant nothing.
 */
public enum AccessRole {

	OWNER("Dono"),

	ADMIN("Administrador"),

	MEMBER("Membro");

	private final String label;

	AccessRole(String label) {
		this.label = label;
	}

	/** Name shown to people (Portuguese). */
	public String label() {
		return label;
	}

}
