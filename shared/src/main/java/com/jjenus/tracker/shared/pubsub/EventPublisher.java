package com.jjenus.tracker.shared.pubsub;

public interface EventPublisher {
    void publish(DomainEvent event);
}
