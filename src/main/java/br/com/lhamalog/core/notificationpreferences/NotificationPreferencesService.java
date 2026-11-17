package br.com.lhamalog.core.notificationpreferences;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import br.com.lhamalog.core.product.Product;
import br.com.lhamalog.core.product.ProductService;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;

@Service
public class NotificationPreferencesService {
    private final NotificationPreferencesRepository preferences;
    private final NotificationCatalog catalog;
    private final ProductService products;

    public NotificationPreferencesService(NotificationPreferencesRepository preferences,
            NotificationCatalog catalog, ProductService products) {
        this.preferences = preferences;
        this.catalog = catalog;
        this.products = products;
    }
    public NotificationPreferencesView get(CompanySession session) {
        return preferences.findById(key(session)).map(NotificationPreferencesView::from)
                .orElseGet(NotificationPreferencesView::defaults);
    }
    public NotificationPreferencesView save(CompanySession session, JsonNode body) {
        var draft = NotificationPreferenceRules.read(body);
        validateSelection(draft.types(), catalog.types().keySet(), "types");
        validateSelection(draft.severities(), catalog.severities().keySet(), "severities");
        if (!draft.products().all()) validateSelection(draft.products(), accessibleProducts(session), "products");
        String id = key(session);
        var current = preferences.findById(id).orElse(null);
        Long version = current == null ? null : current.version();
        if (!Objects.equals(version, draft.version())) {
            throw new OptimisticLockingFailureException("Notification preferences version conflict");
        }
        var next = new NotificationPreferences(id, session.tenantId(), session.userId(), true,
                draft.email(), draft.push(), draft.products(), draft.types(), draft.severities(), version);
        return NotificationPreferencesView.from(current == null ? preferences.insert(next) : preferences.save(next));
    }

    /** Applies optional-notification preferences; caller must supply a trusted, active member session.
     * This is not a delivery transport and must not override mandatory notification policies. */
    public boolean allows(CompanySession recipient, String channel, String productId, String type, String severity) {
        if (productId == null || !accessibleProducts(recipient).contains(productId)) return false;
        var value = get(recipient);
        boolean enabled = switch (channel == null ? "" : channel) {
            case "WEB" -> value.web();
            case "EMAIL" -> value.email();
            case "PUSH" -> value.push();
            default -> false;
        };
        return enabled && value.products().matches(productId)
                && matchesCatalog(value.types(), catalog.types().keySet(), type)
                && matchesCatalog(value.severities(), catalog.severities().keySet(), severity);
    }
    private static boolean matchesCatalog(PreferenceSelection selection, Set<String> available, String value) {
        return value != null && !value.isBlank()
                && (selection.all() || (available.contains(value) && selection.ids().contains(value)));
    }
    private Set<String> accessibleProducts(CompanySession session) {
        return products.list(session).stream().map(Product::id).collect(Collectors.toSet());
    }
    private static void validateSelection(PreferenceSelection selection, Set<String> available, String field) {
        if (!selection.all() && !available.containsAll(selection.ids())) {
            throw new FieldValidationException(Map.of(field, "A seleção contém itens indisponíveis. Recarregue as opções."));
        }
    }
    private static String key(CompanySession session) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(session.tenantId().getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(session.userId().getBytes(StandardCharsets.UTF_8));
    }
}
