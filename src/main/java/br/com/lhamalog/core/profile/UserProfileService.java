package br.com.lhamalog.core.profile;

import java.time.Instant;
import java.util.Objects;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;

@Service
public class UserProfileService {
	private final UserProfileRepository profiles;

	public UserProfileService(UserProfileRepository profiles) {
		this.profiles = profiles;
	}

	public UserProfileView get(String userId) {
		return profiles.findById(userId).map(UserProfileView::from).orElseGet(UserProfileView::empty);
	}

	public UserProfileView save(String userId, JsonNode body) {
		UserProfileRules.Draft draft = UserProfileRules.read(body);
		if (!draft.errors().isEmpty()) {
			throw new FieldValidationException(draft.errors());
		}
		UserProfile current = profiles.findById(userId).orElse(null);
		Long currentVersion = current == null ? null : current.version();
		if (!Objects.equals(currentVersion, draft.version())) {
			throw new OptimisticLockingFailureException("User profile version conflict");
		}
		UserProfile saved = profiles.save(new UserProfile(userId, draft.displayName(), draft.language(), draft.timezone(),
				storedText(draft.bio(), current == null ? null : current.bio()),
				storedText(draft.jobTitle(), current == null ? null : current.jobTitle()), Instant.now(),
				currentVersion));
		return UserProfileView.from(saved);
	}

	private static String storedText(UserProfileRules.OptionalText text, String current) {
		if (!text.present()) {
			return current;
		}
		if (text.value() == null || text.value().isBlank()) {
			return null;
		}
		return text.value();
	}
}
