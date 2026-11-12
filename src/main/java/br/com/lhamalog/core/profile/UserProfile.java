package br.com.lhamalog.core.profile;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("UserProfile")
public record UserProfile(@Id String id, String displayName, String language, String timezone, Instant updatedAt,
		@Version Long version) {}
