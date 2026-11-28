package br.com.lhamalog.core.finderalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * {@code POST /internal/v1/alertas/finder}. Authentication stays on the single {@code /internal/v1/**} chain. The
 * process answers HTTP; TLS in the local test is the Caddy proxy in front of port 8080.
 */
@RestController
@RequestMapping("/internal/v1")
public class FinderAlertController {

	private static final Logger log = LoggerFactory.getLogger(FinderAlertController.class);

	private final FinderAlertService alerts;

	public FinderAlertController(FinderAlertService alerts) {
		this.alerts = alerts;
	}

	@PostMapping("/alertas/finder")
	public ResponseEntity<FinderAlertResponse> accept(@RequestBody JsonNode body) {
		log.info("event=occurrence.alert.received | Finder alert received");
		FinderAlertService.Result result = alerts.accept(FinderAlertRequests.read(body));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
	}

}
