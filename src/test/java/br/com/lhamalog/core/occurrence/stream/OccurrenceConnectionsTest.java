package br.com.lhamalog.core.occurrence.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import br.com.lhamalog.core.occurrence.application.OccurrenceEvent;
import br.com.lhamalog.core.shared.CompanySession;
import tools.jackson.databind.json.JsonMapper;

/** Spec 015: who gets a notice, and when a connection is closed. */
class OccurrenceConnectionsTest {

	private static final Instant NOW = Instant.parse("2026-11-25T18:00:00Z");

	private final MutableClock clock = new MutableClock();

	private final StreamAuthenticator authenticator = mock(StreamAuthenticator.class);

	private final OccurrenceConnections connections = new OccurrenceConnections(authenticator,
			new OccurrenceStreamProperties(null, null, null, null), JsonMapper.builder().build(), clock);

	private WebSocketSession session(String id) {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		when(session.isOpen()).thenReturn(true);
		return session;
	}

	private WebSocketSession ready(String id, String tenant, String user, Set<String> products) {
		WebSocketSession session = session(id);
		connections.open(session);
		CompanySession company = new CompanySession(tenant, user, true);
		when(authenticator.authenticate("token-" + id))
			.thenReturn(new StreamAuthenticator.Result.Accepted(company, NOW.plusSeconds(60)));
		when(authenticator.visibleProducts(company)).thenReturn(products);
		connections.authenticate(session, "token-" + id);
		return session;
	}

	private static OccurrenceEvent event(String tenant, String product) {
		return new OccurrenceEvent(tenant, product, "o-1", "ERROR", OccurrenceEvent.Change.CREATED, NOW);
	}

	@SuppressWarnings("unchecked")
	private static int sent(WebSocketSession session) throws Exception {
		ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
		verify(session, org.mockito.Mockito.atLeast(0)).sendMessage(captor.capture());
		return (int) captor.getAllValues().stream().filter(TextMessage.class::isInstance).count();
	}

	@Test
	void noticeGoesOnlyToTheCompanyConnectionsThatSeeTheProduct() throws Exception {
		WebSocketSession sees = ready("a", "t-1", "u-1", Set.of("p-1"));
		WebSocketSession other = ready("b", "t-1", "u-2", Set.of("p-2"));
		WebSocketSession otherCompany = ready("c", "t-2", "u-3", Set.of("p-1"));
		WebSocketSession pending = session("d");
		connections.open(pending);
		connections.dispatch(event("t-1", "p-1"));
		assertThat(sent(sees)).isEqualTo(2); // ready + notice
		assertThat(sent(other)).isEqualTo(1);
		assertThat(sent(otherCompany)).isEqualTo(1);
		verify(pending, never()).sendMessage(any());
	}

	@Test
	void pendingConnectionIsClosedAfterTheAuthTimeout() throws Exception {
		WebSocketSession pending = session("p");
		connections.open(pending);
		clock.now = NOW.plusSeconds(5);
		connections.maintain();
		verify(pending, never()).close(any());
		clock.now = NOW.plusSeconds(11);
		connections.maintain();
		verify(pending).close(OccurrenceConnections.UNAUTHENTICATED);
		assertThat(connections.size()).isZero();
	}

	@Test
	void expiredTokenWithoutRenewalIsClosedAfterTheGrace() throws Exception {
		WebSocketSession session = ready("e", "t-1", "u-1", Set.of("p-1"));
		clock.now = NOW.plusSeconds(85);
		connections.maintain();
		verify(session, never()).close(any());
		clock.now = NOW.plusSeconds(91);
		connections.maintain();
		verify(session).close(OccurrenceConnections.UNAUTHENTICATED);
	}

	@Test
	void visibleProductsAreReadAgainAfterTheRefreshPeriod() {
		ready("r", "t-1", "u-1", Set.of("p-1"));
		clock.now = NOW.plus(Duration.ofSeconds(61));
		connections.maintain();
		verify(authenticator, org.mockito.Mockito.times(2)).visibleProducts(any());
	}

	private static final class MutableClock extends Clock {

		Instant now = NOW;

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
