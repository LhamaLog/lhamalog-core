package br.com.lhamalog.core.notificationpreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;

final class NotificationPreferenceRules {
    private NotificationPreferenceRules() {}
    static NotificationPreferencesView read(JsonNode body) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (body == null || !body.isObject()) {
            throw new FieldValidationException(Map.of("body", "Os dados enviados são inválidos."));
        }
        boolean web = flag(body, "web", errors);
        boolean email = flag(body, "email", errors);
        boolean push = flag(body, "push", errors);
        if (!web) errors.put("web", "O canal web é obrigatório.");
        var products = selection(body.get("products"), "products", errors);
        var types = selection(body.get("types"), "types", errors);
        var severities = selection(body.get("severities"), "severities", errors);
        JsonNode version = body.get("version");
        Long parsedVersion = null;
        if (version == null || (!version.isNull()
                && (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0))) {
            errors.put("version", "Informe uma versão válida.");
        } else if (!version.isNull()) parsedVersion = version.longValue();
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
        return new NotificationPreferencesView(web, email, push, products, types, severities, parsedVersion);
    }
    private static boolean flag(JsonNode body, String field, Map<String, String> errors) {
        JsonNode value = body.get(field);
        if (value == null || !value.isBoolean()) {
            errors.put(field, "Informe verdadeiro ou falso.");
            return false;
        }
        return value.booleanValue();
    }
    private static PreferenceSelection selection(JsonNode value, String field, Map<String, String> errors) {
        if (value == null || !value.isObject() || !value.path("all").isBoolean()
                || !value.path("ids").isArray() || value.path("ids").size() > 500) {
            errors.put(field, "Informe uma seleção válida, com até 500 itens.");
            return PreferenceSelection.any();
        }
        var ids = new ArrayList<String>();
        for (JsonNode id : value.get("ids")) {
            if (!id.isTextual() || id.asText().isBlank() || id.asText().length() > 200
                    || !id.asText().equals(id.asText().trim())) {
                errors.put(field, "Seleção contém um identificador inválido.");
            } else ids.add(id.asText());
        }
        boolean all = value.get("all").booleanValue();
        if ((all && !ids.isEmpty()) || (!all && ids.isEmpty()) || new HashSet<>(ids).size() != ids.size()) {
            errors.put(field, "Selecione Todos ou ao menos um item, sem repetições.");
        }
        return new PreferenceSelection(all, ids);
    }
}
