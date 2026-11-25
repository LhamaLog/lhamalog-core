package br.com.lhamalog.core.notificationpreferences;

public record NotificationPreferencesView(boolean web, boolean email, boolean push,
        PreferenceSelection products, PreferenceSelection types, PreferenceSelection severities, Long version) {
    public static NotificationPreferencesView defaults() {
        return new NotificationPreferencesView(true, false, false,
                PreferenceSelection.any(), PreferenceSelection.any(), PreferenceSelection.any(), null);
    }
    public static NotificationPreferencesView from(NotificationPreferences value) {
        return new NotificationPreferencesView(value.web(), value.email(), value.push(),
                value.products(), value.types(), value.severities(), value.version());
    }
}
