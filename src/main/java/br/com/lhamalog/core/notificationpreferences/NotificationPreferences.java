package br.com.lhamalog.core.notificationpreferences;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notification_preferences")
public record NotificationPreferences(@Id String id, String tenantId, String userId,
        boolean web, boolean email, boolean push, PreferenceSelection products,
        PreferenceSelection types, PreferenceSelection severities, @Version Long version) {}
