package br.com.lhamalog.core.finderalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.lhamalog.core.occurrence.application.InternalApiException;
import br.com.lhamalog.core.product.ProductRepository;

/**
 * Accepts a Finder alert once per key. The first write answers as created and notifies the screen; a repeat answers
 * as a duplicate and does not notify again. A product that does not belong to the tenant is refused before any write.
 */
@Service
public class FinderAlertService {

	private static final Logger log = LoggerFactory.getLogger(FinderAlertService.class);

	private final ProductRepository products;

	private final FinderAlertRepository alerts;

	private final FinderAlertNotifier notices;

	public FinderAlertService(ProductRepository products, FinderAlertRepository alerts, FinderAlertNotifier notices) {
		this.products = products;
		this.alerts = alerts;
		this.notices = notices;
	}

	public Result accept(FinderAlertCommand command) {
		if (products.findByIdAndTenantId(command.productId(), command.tenantId()).isEmpty()) {
			throw new InternalApiException(HttpStatus.FORBIDDEN, InternalApiException.ACCESS_DENIED);
		}
		boolean created = alerts.insert(command);
		FinderAlertResponse body = new FinderAlertResponse(true, command.tenantId(), command.productId(),
				command.batchId(), command.motivo());
		if (!created) {
			log.info("event=occurrence.alert.duplicate | Finder alert already stored | tenantId={} productId={} batchId={}",
					command.tenantId(), command.productId(), command.batchId());
			return new Result(false, body);
		}
		log.info("event=occurrence.alert.accepted | Finder alert stored | tenantId={} productId={} batchId={}",
				command.tenantId(), command.productId(), command.batchId());
		try {
			notices.notifyAccepted(command);
		}
		catch (RuntimeException failure) {
			log.warn("event=occurrence.alert.accepted | Finder alert stored but the screen was not notified | error={}",
					failure.getClass().getSimpleName());
		}
		return new Result(true, body);
	}

	/** {@code created} is the first durable write of the key. */
	public record Result(boolean created, FinderAlertResponse body) {
	}

}
