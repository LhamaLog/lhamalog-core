package br.com.lhamalog.core.profile;

public record UserProfileView(String displayName, String language, String timezone, String bio, String jobTitle,
		Long version) {
	static UserProfileView empty() {
		return new UserProfileView("", "pt-BR", "America/Sao_Paulo", "", "", null);
	}

	static UserProfileView from(UserProfile profile) {
		return new UserProfileView(profile.displayName(), profile.language(), profile.timezone(), text(profile.bio()),
				text(profile.jobTitle()), profile.version());
	}

	private static String text(String value) {
		return value == null || value.isBlank() ? "" : value;
	}
}
