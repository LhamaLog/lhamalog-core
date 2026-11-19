package br.com.lhamalog.core.dashboard.api;

import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.CompanyMetrics;
import br.com.lhamalog.core.dashboard.api.DashboardMetricsResponses.ProductMetrics;
import br.com.lhamalog.core.dashboard.application.DashboardMetricsService;
import br.com.lhamalog.core.shared.ApiStatusException;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardMetricsController {

	private final DashboardMetricsService metrics;
	private final CompanySessionResolver sessions;

	public DashboardMetricsController(DashboardMetricsService metrics, CompanySessionResolver sessions) {
		this.metrics = metrics;
		this.sessions = sessions;
	}

	@GetMapping("/products/{productId}/metrics")
	public ProductMetrics productMetrics(
			@PathVariable String productId,
			@RequestParam String from,
			@RequestParam String to,
			@RequestParam(required = false) String severities) {
		return metrics.productMetrics(sessions.current(), productId, parseInstant(from, "from"),
				parseInstant(to, "to"), severities);
	}

	@GetMapping("/company/metrics")
	public CompanyMetrics companyMetrics(
			@RequestParam String from,
			@RequestParam String to,
			@RequestParam(required = false) String productIds,
			@RequestParam(required = false) String severities) {
		return metrics.companyMetrics(sessions.current(), parseInstant(from, "from"), parseInstant(to, "to"),
				productIds, severities);
	}

	private static Instant parseInstant(String raw, String name) {
		if (raw == null || raw.isBlank()) {
			throw new ApiStatusException(HttpStatus.BAD_REQUEST,
					"O parâmetro " + name + " é obrigatório.", DashboardMetricsService.INVALID_FILTER);
		}
		try {
			return Instant.parse(raw.trim());
		}
		catch (DateTimeParseException exception) {
			throw new ApiStatusException(HttpStatus.BAD_REQUEST,
					"O parâmetro " + name + " deve ser um instante ISO-8601.",
					DashboardMetricsService.INVALID_FILTER);
		}
	}
}
