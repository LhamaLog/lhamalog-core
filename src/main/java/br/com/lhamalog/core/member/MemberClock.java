package br.com.lhamalog.core.member;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class MemberClock {
	private volatile Clock clock = Clock.systemUTC();
	private volatile Duration interval = MemberObservation.PRODUCTION_INTERVAL;

	public Instant instant() {
		return clock.instant();
	}

	public Duration interval() {
		return interval;
	}

	void setClock(Clock clock) {
		this.clock = clock;
	}

	void setInterval(Duration interval) {
		this.interval = interval;
	}

	void reset() {
		clock = Clock.systemUTC();
		interval = MemberObservation.PRODUCTION_INTERVAL;
	}
}
