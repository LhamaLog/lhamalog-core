package br.com.lhamalog.core.finderalert;

import org.springframework.stereotype.Component;

import br.com.lhamalog.core.occurrence.stream.OccurrenceConnections;

/**
 * Delivers the first Finder alert on {@code /ws/occurrences}. Does not publish on {@code lhamalog:occurrence-events}
 * or {@code lhamalog:notifications:events}.
 */
@Component
public class WebSocketFinderAlertNotifier implements FinderAlertNotifier {

	private final OccurrenceConnections connections;

	public WebSocketFinderAlertNotifier(OccurrenceConnections connections) {
		this.connections = connections;
	}

	@Override
	public void notifyAccepted(FinderAlertCommand command) {
		connections.dispatchFinderAlert(command.tenantId(), command.productId(), command.batchId(), command.motivo());
	}

}
