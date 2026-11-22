package br.com.lhamalog.core.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.billing.adapter.in.web.AsaasWebhookCredential;
import br.com.lhamalog.core.billing.adapter.out.fake.AsaasWebhookSender;

/**
 * Fake of the simulator's HTTP sender: MockMvc tests have no running server, so the events are recorded and the test
 * delivers them to the webhook itself. The HTTP sender is covered by {@code AsaasWebhookSenderTest}.
 */
public class RecordingAsaasWebhookSender extends AsaasWebhookSender {

	private final List<String> sent = new CopyOnWriteArrayList<>();

	private volatile int status = 200;

	public RecordingAsaasWebhookSender() {
		super(RestClient.builder(), "http://unused.invalid", AsaasWebhookCredential.generatedWhenBlank(null));
	}

	@Override
	public int send(String eventJson) {
		sent.add(eventJson);
		return status;
	}

	public List<String> sent() {
		return List.copyOf(sent);
	}

	public void answer(int status) {
		this.status = status;
	}

	public void reset() {
		sent.clear();
		status = 200;
	}

}
