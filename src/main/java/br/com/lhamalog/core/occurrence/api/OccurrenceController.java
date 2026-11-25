package br.com.lhamalog.core.occurrence.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.occurrence.application.OccurrenceFilter;
import br.com.lhamalog.core.occurrence.application.OccurrenceService;
import br.com.lhamalog.core.shared.CompanySessionResolver;

/**
 * Read-only occurrence routes for the Web (LLT-135). Parameters arrive as text and are validated by
 * {@link OccurrenceFilter}, so an unknown type or a bad date becomes a 400 with field errors.
 */
@RestController
@RequestMapping("/api/occurrences")
public class OccurrenceController {

	private final OccurrenceService service;

	private final CompanySessionResolver sessions;

	public OccurrenceController(OccurrenceService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@GetMapping
	public OccurrenceResponses.Page list(@RequestParam(required = false) String page,
			@RequestParam(required = false) String size, @RequestParam(required = false) List<String> type,
			@RequestParam(required = false) List<String> status, @RequestParam(required = false) List<String> productId,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String q) {
		return service.list(sessions.current(), OccurrenceFilter.parse(page, size, type, status, productId, from, to, q));
	}

	@GetMapping("/{id}")
	public OccurrenceResponses.Detail detail(@PathVariable String id) {
		return service.detail(sessions.current(), id);
	}
}
