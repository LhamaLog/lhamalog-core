package br.com.lhamalog.core.webhook.adapter.in.web;

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

import br.com.lhamalog.core.webhook.adapter.in.web.ClerkWebhookEventParser.InvalidWebhookPayloadException;
import br.com.lhamalog.core.webhook.application.ClerkWebhookProcessor;
import br.com.lhamalog.core.webhook.domain.ClerkWebhookEvent;

/**
 * Public Clerk webhook endpoint (ADR_022/141). Authenticated by the Svix signature over the raw body, not by a
 * session token. 2xx tells Svix to stop; 4xx/5xx make it retry.
 */
@RestController
public class ClerkWebhookController {

	public static final String PATH = "/webhooks/clerk";

	static final String OK = "{\"status\":\"ok\"}";

	static final String INVALID = "{\"error\":\"invalid_webhook\"}";

	static final String FAILED = "{\"error\":\"processing_failed\"}";

	private static final Logger log = LoggerFactory.getLogger(ClerkWebhookController.class);

	/** svix-id comes from the caller; it is logged only when it looks like an id. */
	private static final Pattern LOGGABLE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private final SvixSignatureVerifier verifier;

	private final ClerkWebhookEventParser parser;

	private final ClerkWebhookProcessor processor;

	public ClerkWebhookController(SvixSignatureVerifier verifier, ClerkWebhookEventParser parser,
			ClerkWebhookProcessor processor) {
		this.verifier = verifier;
		this.parser = parser;
		this.processor = processor;
	}

	@PostMapping(PATH)
	ResponseEntity<String> receive(@RequestHeader(name = "svix-id", required = false) String svixId,
			@RequestHeader(name = "svix-timestamp", required = false) String svixTimestamp,
			@RequestHeader(name = "svix-signature", required = false) String svixSignature,
			@RequestBody(required = false) byte[] body) {
		if (!verifier.verify(svixId, svixTimestamp, svixSignature, body)) {
			log.warn("Rejected Clerk webhook with invalid signature svixId={}", loggable(svixId));
			return json(HttpStatus.BAD_REQUEST, INVALID);
		}
		ClerkWebhookEvent event;
		try {
			event = parser.parse(body);
		}
		catch (InvalidWebhookPayloadException ex) {
			log.warn("Rejected Clerk webhook svixId={}: {}", loggable(svixId), ex.getMessage());
			return json(HttpStatus.BAD_REQUEST, INVALID);
		}
		try {
			processor.process(svixId, event);
			return json(HttpStatus.OK, OK);
		}
		catch (RuntimeException ex) {
			log.error("Failed to process Clerk webhook svixId={} type={} error={}", loggable(svixId), event.type(),
					ex.getClass().getSimpleName());
			return json(HttpStatus.INTERNAL_SERVER_ERROR, FAILED);
		}
	}

	private static ResponseEntity<String> json(HttpStatus status, String body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private static String loggable(String svixId) {
		return (svixId != null && LOGGABLE_ID.matcher(svixId).matches()) ? svixId : "<invalid>";
	}

}
