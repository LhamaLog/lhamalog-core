package br.com.lhamalog.core.billing.adapter.in.web;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookParser.InvalidAsaasPayloadException;
import br.com.lhamalog.core.billing.application.GatewayEventProcessor;
import br.com.lhamalog.core.billing.domain.GatewayNotification;

/**
 * Public Asaas webhook endpoint (spec 010, contracts/api.md). Authenticated by the {@code asaas-access-token} header,
 * not by a session token. 200 tells Asaas to stop; an error makes it resend, so errors are returned only when a resend
 * can help (Asaas pauses the queue after 15 consecutive failures).
 */
@RestController
public class AsaasWebhookController {

	public static final String PATH = "/webhooks/asaas";

	public static final String TOKEN_HEADER = "asaas-access-token";

	static final String RECEIVED = "{\"received\":true}";

	static final String INVALID_TOKEN = "{\"error\":\"invalid_token\"}";

	static final String INVALID = "{\"error\":\"invalid_webhook\"}";

	static final String IN_PROGRESS = "{\"error\":\"in_progress\"}";

	static final String FAILED = "{\"error\":\"processing_failed\"}";

	private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

	/** Event ids come from the caller; one is logged only when it looks like an id. */
	private static final Pattern LOGGABLE_ID = Pattern.compile("[A-Za-z0-9_&-]{1,128}");

	private final AsaasWebhookCredential credential;

	private final AsaasWebhookParser parser;

	private final GatewayEventProcessor processor;

	public AsaasWebhookController(AsaasWebhookCredential credential, AsaasWebhookParser parser,
			GatewayEventProcessor processor) {
		this.credential = credential;
		this.parser = parser;
		this.processor = processor;
	}

	@PostMapping(PATH)
	ResponseEntity<String> receive(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
			@RequestBody(required = false) byte[] body) {
		if (!credential.matches(token)) {
			log.warn("event=billing.webhook.rejected | Asaas webhook with missing or wrong token | tokenPresent={}",
					token != null);
			return json(HttpStatus.UNAUTHORIZED, INVALID_TOKEN);
		}
		GatewayNotification notification;
		try {
			notification = parser.parse(body);
		}
		catch (InvalidAsaasPayloadException ex) {
			log.warn("event=billing.webhook.invalid | Asaas webhook body rejected | reason={}", ex.getMessage());
			return json(HttpStatus.BAD_REQUEST, INVALID);
		}
		try {
			return switch (processor.process(notification)) {
				case PROCESSED, DUPLICATE -> json(HttpStatus.OK, RECEIVED);
				case IN_PROGRESS -> json(HttpStatus.CONFLICT, IN_PROGRESS);
			};
		}
		catch (RuntimeException ex) {
			// Already logged and recorded as FAILED by the processor.
			log.debug("Asaas webhook eventId={} answered with 500", loggable(notification.eventId()));
			return json(HttpStatus.INTERNAL_SERVER_ERROR, FAILED);
		}
	}

	private static ResponseEntity<String> json(HttpStatus status, String body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private static String loggable(String eventId) {
		return (eventId != null && LOGGABLE_ID.matcher(eventId).matches()) ? eventId : "<invalid>";
	}

}
