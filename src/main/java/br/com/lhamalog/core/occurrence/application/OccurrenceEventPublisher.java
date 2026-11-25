package br.com.lhamalog.core.occurrence.application;

import java.util.List;

/**
 * Sends the notices of an accepted delivery to every Core instance (spec 015, research R4). Must never fail the
 * delivery: an implementation handles its own errors.
 */
public interface OccurrenceEventPublisher {

	void publish(List<OccurrenceEvent> events);

}
