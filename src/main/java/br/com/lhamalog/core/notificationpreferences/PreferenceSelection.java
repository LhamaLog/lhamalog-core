package br.com.lhamalog.core.notificationpreferences;

import java.util.List;

public record PreferenceSelection(boolean all, List<String> ids) {
    public PreferenceSelection { ids = List.copyOf(ids); }
    public static PreferenceSelection any() { return new PreferenceSelection(true, List.of()); }
    public boolean matches(String id) { return all || ids.contains(id); }
}
