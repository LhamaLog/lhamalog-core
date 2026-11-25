package br.com.lhamalog.core.notificationrole.application;

/**
 * The five notification roles every company has (ADR_136/137). Owner and administrator are immutable: they receive
 * everything and their members come from the access role (ADR_140). The others start with empty lists (clarify C4).
 */
public enum DefaultRole {

	OWNER("Dono", true),

	ADMIN("Administrador", true),

	MANAGER("Gestor", false),

	SUPERVISOR("Supervisor", false),

	MEMBER("Membro", false);

	private final String displayName;

	private final boolean immutable;

	DefaultRole(String displayName, boolean immutable) {
		this.displayName = displayName;
		this.immutable = immutable;
	}

	/** Name the role is created with (Portuguese). Editable defaults can be renamed later. */
	public String displayName() {
		return displayName;
	}

	public boolean immutable() {
		return immutable;
	}

}
