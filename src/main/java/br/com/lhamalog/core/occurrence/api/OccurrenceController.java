package br.com.lhamalog.core.occurrence.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.occurrence.application.OccurrenceFilter;
import br.com.lhamalog.core.occurrence.application.OccurrenceService;
import br.com.lhamalog.core.shared.CompanySessionResolver;

/**
 * Occurrence routes for the Web: list and detail (LLT-135, spec 012) and the lifecycle actions (LLT-136, spec 014). Parameters arrive as text and are validated by
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
			@RequestParam(required = false) String q, @RequestParam(required = false) String archived) {
		return service.list(sessions.current(),
				OccurrenceFilter.parse(page, size, type, status, productId, from, to, q, archived));
	}

	@GetMapping("/{id}")
	public OccurrenceResponses.Detail detail(@PathVariable String id) {
		return service.detail(sessions.current(), id);
	}

	/** Spec 014: {@code revision} is the one the user sees; a stale one is a 409 {@code occurrence_changed}. */
	@PutMapping("/{id}/status")
	public OccurrenceResponses.Detail changeStatus(@PathVariable String id, @RequestBody ChangeStatus request) {
		return service.changeStatus(sessions.current(), id, request.status(), request.revision());
	}

	@PostMapping("/{id}/archive")
	public OccurrenceResponses.Detail archive(@PathVariable String id, @RequestBody ChangeRevision request) {
		return service.archive(sessions.current(), id, request.revision());
	}

	@PostMapping("/{id}/unarchive")
	public OccurrenceResponses.Detail unarchive(@PathVariable String id, @RequestBody ChangeRevision request) {
		return service.unarchive(sessions.current(), id, request.revision());
	}

	public record ChangeStatus(String status, String revision) {}

	public record ChangeRevision(String revision) {}
}
