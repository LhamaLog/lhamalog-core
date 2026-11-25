package br.com.lhamalog.core.occurrence.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.occurrence.api.InternalOccurrenceResponses.Confirmacao;
import br.com.lhamalog.core.occurrence.api.InternalOccurrenceResponses.EstadoOcorrencia;
import br.com.lhamalog.core.occurrence.api.InternalOccurrenceResponses.ResultadoConsulta;
import br.com.lhamalog.core.occurrence.application.AnalysisSummary;
import br.com.lhamalog.core.occurrence.application.AnalysisSummaryDeliveryService;
import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.occurrence.application.OccurrenceLookupService;
import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;
import br.com.lhamalog.core.product.ProductRepository;
import tools.jackson.databind.JsonNode;

/**
 * Occurrence Module routes called by the Finder (contract DN-009-2, spec 013). Authenticated by the internal JWT
 * ({@code InternalServiceSecurityConfiguration}); the product must belong to the tenant of the body.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalOccurrenceController {

	private final OccurrenceLookupService lookups;

	private final AnalysisSummaryDeliveryService deliveries;

	private final ProductRepository products;

	private final int maxFindings;

	public InternalOccurrenceController(OccurrenceLookupService lookups, AnalysisSummaryDeliveryService deliveries,
			ProductRepository products, OccurrenceModuleProperties properties) {
		this.lookups = lookups;
		this.deliveries = deliveries;
		this.products = products;
		this.maxFindings = properties.delivery().maxFindings();
	}

	@PostMapping("/ocorrencias/consultas")
	public ResultadoConsulta lookup(@RequestBody JsonNode body) {
		InternalOccurrenceRequests.Lookup request = InternalOccurrenceRequests.readLookup(body);
		requireProductOfTenant(request.tenantId(), request.productId());
		EstadoOcorrencia state = lookups.lookup(request.tenantId(), request.productId(), request.key())
			.map(found -> new EstadoOcorrencia(found.count(), found.firstSeenAt().toString(),
					found.lastSeenAt().toString(), found.status(), found.revision()))
			.orElse(null);
		return new ResultadoConsulta(state);
	}

	@PostMapping("/resumos-analise")
	public ResponseEntity<Confirmacao> deliver(@RequestBody JsonNode body) {
		AnalysisSummary summary = InternalOccurrenceRequests.readSummary(body, maxFindings);
		requireProductOfTenant(summary.key().tenantId(), summary.key().productId());
		AnalysisSummaryDeliveryService.Result result = deliveries.deliver(summary);
		Confirmacao receipt = new Confirmacao(true, result.receiptId(), summary.key().tenantId(),
				summary.key().productId(), summary.key().batchId(), summary.key().execId(), result.repeated());
		return ResponseEntity.status(result.repeated() ? HttpStatus.OK : HttpStatus.CREATED).body(receipt);
	}

	/** Same answer for a missing product, a product of another tenant and an unknown tenant (research R6). */
	private void requireProductOfTenant(String tenantId, String productId) {
		if (products.findByIdAndTenantId(productId, tenantId).isEmpty()) {
			throw new InternalApiException(HttpStatus.FORBIDDEN, InternalApiException.ACCESS_DENIED);
		}
	}

}
