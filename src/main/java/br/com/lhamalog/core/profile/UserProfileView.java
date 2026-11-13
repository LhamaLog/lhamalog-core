package br.com.lhamalog.core.profile;

public record UserProfileView(String displayName, String language, String timezone, Long version) {
	static UserProfileView empty() {
		return new UserProfileView("", "pt-BR", "America/Sao_Paulo", null);
	}

	static UserProfileView from(UserProfile profile) {
		return new UserProfileView(profile.displayName(), profile.language(), profile.timezone(), profile.version());
	}
}
