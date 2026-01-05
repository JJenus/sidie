package com.jjenus.tracker.shared.pubsub;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
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