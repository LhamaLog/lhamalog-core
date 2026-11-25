package br.com.lhamalog.core.occurrence.stream;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code /ws/occurrences} (spec 015, contract {@code contracts/websocket.md}): the only message the screen sends is
 * {@code {"type":"auth","token":"…"}}. The token is never logged.
 */
@Component
public class OccurrenceStreamHandler extends TextWebSocketHandler {

	private final OccurrenceConnections connections;

	private final ObjectMapper mapper;

	public OccurrenceStreamHandler(OccurrenceConnections connections, ObjectMapper mapper) {
		this.connections = connections;
		this.mapper = mapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		connections.open(session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String token = authToken(message.getPayload());
		if (token == null) connections.unexpected(session);
		else connections.authenticate(session, token);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		connections.closed(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		connections.closed(session);
	}

	/** The token of an {@code auth} message, or null for anything else (including invalid JSON). */
	private String authToken(String payload) {
		try {
			JsonNode node = mapper.readTree(payload);
			if (node != null && node.isObject() && "auth".equals(node.path("type").asString(""))
					&& node.path("token").isString()) {
				return node.path("token").stringValue();
			}
		}
		catch (RuntimeException invalid) {
			// Not JSON: treated as an unexpected message.
		}
		return null;
	}

}
