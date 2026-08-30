package com.jjenus.tracker.shared.pubsub;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public abstract class DomainEvent {
    private final String eventId;
    private final Instant occurredOn;

    public DomainEvent() {
        this(Clock.systemUTC(), UUID.randomUUID());
    }

    public DomainEvent(Clock clock, UUID eventId) {
        this.eventId = eventId.toString();
        this.occurredOn = clock.instant();
    }

    public DomainEvent(Clock clock) {
        this(clock, UUID.randomUUID());
    }

    public String getEventId() { return eventId; }
    public Instant getOccurredOn() { return occurredOn; }
}