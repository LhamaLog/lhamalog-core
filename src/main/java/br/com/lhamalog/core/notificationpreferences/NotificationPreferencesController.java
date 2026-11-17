package br.com.lhamalog.core.notificationpreferences;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/user/notification-preferences")
public class NotificationPreferencesController {
    private final NotificationPreferencesService service;
    private final CompanySessionResolver sessions;
    private final NotificationCatalog catalog;
    public NotificationPreferencesController(NotificationPreferencesService service,
            CompanySessionResolver sessions, NotificationCatalog catalog) {
        this.service = service;
        this.sessions = sessions;
        this.catalog = catalog;
    }
    @GetMapping
    public NotificationPreferencesView get() {
        return service.get(sessions.current());
    }
    @GetMapping("/catalog")
    public NotificationCatalog.View catalog() {
        sessions.current();
        return catalog.view();
    }
    @PutMapping
    public NotificationPreferencesView save(@RequestBody String body) {
        var session = sessions.current();
        JsonNode json;
        try { json = JsonMapper.shared().readTree(body); }
        catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos.");
        }
        return service.save(session, json);
    }
}
