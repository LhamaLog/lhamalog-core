package br.com.lhamalog.core.usage.api;

import br.com.lhamalog.core.usage.application.UsageCycleSummary;
import br.com.lhamalog.core.usage.application.UsageFactService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/internal/usage-facts")
public class UsageFactsController {
	private final UsageFactService usageFacts;

	public UsageFactsController(UsageFactService usageFacts) {
		this.usageFacts = usageFacts;
	}

	@PostMapping
	public ResponseEntity<Void> record(@Valid @RequestBody UsageFactRequest request) {
		return switch (usageFacts.record(request)) {
			case CREATED -> ResponseEntity.status(HttpStatus.CREATED).build();
			case DUPLICATE -> ResponseEntity.ok().build();
			case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
		};
	}

	@GetMapping
	public List<UsageFactRequest> find(@RequestParam @NotBlank String tenantId,
			@RequestParam @NotBlank String productId, @RequestParam Instant from, @RequestParam Instant to) {
		if (!from.isBefore(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time window");
		return usageFacts.find(tenantId, productId, from, to);
	}

	@GetMapping("/current-cycle")
	public UsageCycleSummary currentCycle(@RequestParam @NotBlank String tenantId,
			@RequestParam @NotBlank String productId) {
		return usageFacts.currentCycle(tenantId, productId);
	}
}
