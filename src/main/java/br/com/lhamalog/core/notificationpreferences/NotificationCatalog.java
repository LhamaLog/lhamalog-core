package br.com.lhamalog.core.notificationpreferences;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("lhamalog.notification-preferences.catalog")
public record NotificationCatalog(Map<String, String> types, Map<String, String> severities) {
    public NotificationCatalog {
        types = checked(types);
        severities = checked(severities);
    }
    private static Map<String, String> checked(Map<String, String> values) {
        if (values == null) return Map.of();
        if (values.size() > 500 || values.entrySet().stream().anyMatch(entry ->
                entry.getKey().isBlank() || entry.getKey().length() > 200
                || !entry.getKey().equals(entry.getKey().trim())
                || entry.getValue() == null || entry.getValue().isBlank() || entry.getValue().length() > 200)) {
            throw new IllegalArgumentException("Invalid notification catalog");
        }
        return Map.copyOf(values);
    }
    public record Option(String id, String label) {}
    public record View(List<Option> types, List<Option> severities) {}
    public View view() { return new View(options(types), options(severities)); }
    private static List<Option> options(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Option(entry.getKey(), entry.getValue())).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationCatalog.class)
    static class CatalogConfiguration {}
}
