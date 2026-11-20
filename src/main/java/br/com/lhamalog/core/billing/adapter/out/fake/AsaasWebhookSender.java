package br.com.lhamalog.core.billing.adapter.out.fake;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookController;
import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;

/**
 * Delivers a simulated Asaas event to the Core's own webhook over HTTP, with the webhook token, exactly as Asaas
 * would (research R9). Local profile only.
 */
public class AsaasWebhookSender {

	/** Status that stands for "the webhook could not be reached". */
	public static final int UNREACHABLE = -1;

	private final RestClient restClient;

	private final String webhookUrl;

	private final AsaasWebhookCredential credential;

	public AsaasWebhookSender(RestClient.Builder builder, String webhookUrl, AsaasWebhookCredential credential) {
		this.restClient = builder.build();
		this.webhookUrl = webhookUrl;
		this.credential = credential;
	}

	/** @return the HTTP status answered by the webhook, or {@link #UNREACHABLE} */
	public int send(String eventJson) {
		try {
			return restClient.post()
				.uri(webhookUrl)
				.contentType(MediaType.APPLICATION_JSON)
				.header(AsaasWebhookController.TOKEN_HEADER, credential.value())
				.body(eventJson)
				.retrieve()
				.toBodilessEntity()
				.getStatusCode()
				.value();
		}
		catch (RestClientResponseException ex) {
			return ex.getStatusCode().value();
		}
		catch (RestClientException ex) {
			return UNREACHABLE;
		}
	}

}
