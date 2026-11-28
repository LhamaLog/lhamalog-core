package br.com.lhamalog.core.occurrence.stream;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import br.com.lhamalog.core.access.application.UserHash;
import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.shared.CompanySession;
import tools.jackson.databind.ObjectMapper;

/**
 * Open occurrence connections of this Core instance (spec 015): PENDING until a valid {@code auth}, then READY with the
 * company, the person, the visible products (refreshed every {@code productsRefresh}) and the token expiry. Thread
 * safe; sending goes through a {@link ConcurrentWebSocketSessionDecorator}.
 */
@Component
public class OccurrenceConnections {

	static final CloseStatus UNAUTHENTICATED = new CloseStatus(4401, "unauthenticated");

	static final CloseStatus FORBIDDEN = new CloseStatus(4403, "forbidden");

	private static final Logger log = LoggerFactory.getLogger(OccurrenceConnections.class);

	private final Map<String, Connection> connections = new ConcurrentHashMap<>();

	private final StreamAuthenticator authenticator;

	private final OccurrenceStreamProperties settings;

	private final ObjectMapper mapper;

	private final Clock clock;

	public OccurrenceConnections(StreamAuthenticator authenticator, OccurrenceStreamProperties settings,
			ObjectMapper mapper, Clock clock) {
		this.authenticator = authenticator;
		this.settings = settings;
		this.mapper = mapper;
		this.clock = clock;
	}

	/** One connection; mutable fields are only changed under its own lock. */
	static final class Connection {

		final WebSocketSession session;

		final Instant openedAt;

		CompanySession company;

		Set<String> products = Set.of();

		Instant expiresAt;

		Instant productsReadAt;

		Connection(WebSocketSession session, Instant openedAt) {
			this.session = new ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024);
			this.openedAt = openedAt;
		}

		synchronized boolean ready() {
			return company != null;
		}

	}

	void open(WebSocketSession session) {
		connections.put(session.getId(), new Connection(session, clock.instant()));
	}

	void closed(WebSocketSession session) {
		Connection removed = connections.remove(session.getId());
		if (removed != null && removed.ready()) {
			log.debug("event=occurrence.stream.closed | Occurrence connection closed | tenantId={} userHash={}",
					removed.company.tenantId(), UserHash.of(removed.company.userId()));
		}
	}

	/** Handles an {@code auth} message: first one makes the connection READY, later ones renew the token. */
	void authenticate(WebSocketSession session, String token) {
		Connection connection = connections.get(session.getId());
		if (connection == null) return;
		StreamAuthenticator.Result result = authenticator.authenticate(token);
		if (result instanceof StreamAuthenticator.Result.InvalidToken) {
			refuse(connection, UNAUTHENTICATED, "invalid_token");
			return;
		}
		if (!(result instanceof StreamAuthenticator.Result.Accepted accepted)) {
			refuse(connection, FORBIDDEN, "access_denied");
			return;
		}
		boolean first;
		boolean identityChanged;
		synchronized (connection) {
			first = connection.company == null;
			identityChanged = !first && (!connection.company.tenantId().equals(accepted.session().tenantId())
					|| !connection.company.userId().equals(accepted.session().userId()));
		}
		if (identityChanged) {
			refuse(connection, FORBIDDEN, "identity_changed");
			return;
		}
		Set<String> products = authenticator.visibleProducts(accepted.session());
		synchronized (connection) {
			connection.company = accepted.session();
			connection.expiresAt = accepted.expiresAt();
			connection.products = Set.copyOf(products);
			connection.productsReadAt = clock.instant();
		}
		send(connection, Map.of("type", "ready", "expiresAt", String.valueOf(accepted.expiresAt())));
		if (first) {
			log.info("event=occurrence.stream.authenticated | Occurrence connection ready | tenantId={} userHash={}",
					accepted.session().tenantId(), UserHash.of(accepted.session().userId()));
		}
	}

	/** A message other than {@code auth}: closes a PENDING connection, ignored on a READY one. */
	void unexpected(WebSocketSession session) {
		Connection connection = connections.get(session.getId());
		if (connection != null && !connection.ready()) refuse(connection, UNAUTHENTICATED, "missing_auth");
	}

	/** Sends a notice to the READY connections of the company that see the product. */
	public void dispatch(OccurrenceEvent event) {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("type", "occurrence");
		message.put("change", event.change().name());
		message.put("occurrenceId", event.occurrenceId());
		message.put("productId", event.productId());
		message.put("occurrenceType", event.occurrenceType());
		message.put("gravidade", event.gravidade());
		message.put("at", String.valueOf(event.at()));
		sendToProduct(event.tenantId(), event.productId(), message);
	}

	/**
	 * Screen notice of a Finder alert, on the same socket, only for the first durable write. Not published on the
	 * occurrence Redis channel. {@code tenantId} is part of this body.
	 */
	public void dispatchFinderAlert(String tenantId, String productId, String batchId, String motivo) {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("type", "FINDER_ALERT");
		message.put("tenantId", tenantId);
		message.put("productId", productId);
		message.put("batchId", batchId);
		message.put("motivo", motivo);
		sendToProduct(tenantId, productId, message);
	}

	private void sendToProduct(String tenantId, String productId, Map<String, Object> message) {
		for (Connection connection : connections.values()) {
			boolean matches;
			synchronized (connection) {
				matches = connection.company != null && connection.company.tenantId().equals(tenantId)
						&& connection.products.contains(productId);
			}
			if (matches) send(connection, message);
		}
	}

	/** Closes PENDING connections past the auth timeout and READY ones past expiry; refreshes visible products. */
	@Scheduled(fixedDelayString = "${lhamalog.occurrence.stream.maintenance-interval:1s}")
	public void maintain() {
		Instant now = clock.instant();
		for (Connection connection : List.copyOf(connections.values())) {
			CompanySession company;
			Instant expiresAt;
			Instant productsReadAt;
			synchronized (connection) {
				company = connection.company;
				expiresAt = connection.expiresAt;
				productsReadAt = connection.productsReadAt;
			}
			if (company == null) {
				if (connection.openedAt.plus(settings.authTimeout()).isBefore(now)) refuse(connection, UNAUTHENTICATED, "auth_timeout");
				continue;
			}
			if (expiresAt != null && expiresAt.plus(settings.expiryGrace()).isBefore(now)) {
				refuse(connection, UNAUTHENTICATED, "token_expired");
				continue;
			}
			if (productsReadAt == null || productsReadAt.plus(settings.productsRefresh()).isBefore(now)) {
				try {
					Set<String> products = Set.copyOf(authenticator.visibleProducts(company));
					synchronized (connection) {
						connection.products = products;
						connection.productsReadAt = now;
					}
				}
				catch (RuntimeException failure) {
					log.warn("event=occurrence.stream.products_failed | Visible products not refreshed; kept the previous ones | tenantId={} error={}",
							company.tenantId(), failure.getClass().getSimpleName());
				}
			}
		}
	}

	int size() {
		return connections.size();
	}

	private void refuse(Connection connection, CloseStatus status, String reason) {
		log.warn("event=occurrence.stream.refused | Occurrence connection closed | code={} reason={}", status.getCode(),
				reason);
		connections.remove(connection.session.getId());
		try {
			connection.session.close(status);
		}
		catch (IOException | RuntimeException ignored) {
			// Already closed by the client.
		}
	}

	private void send(Connection connection, Map<String, Object> message) {
		try {
			connection.session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
		}
		catch (IOException | RuntimeException failure) {
			connections.remove(connection.session.getId());
		}
	}

}
