package com.jjenus.tracker.shared.pubsub;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    private final String eventId;
    private final Instant occurredOn;

    public DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredOn = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Instant getOccurredOn() { return occurredOn; }
}
