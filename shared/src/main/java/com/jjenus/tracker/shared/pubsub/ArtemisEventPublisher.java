package com.jjenus.tracker.shared.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArtemisEventPublisher implements EventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public ArtemisEventPublisher(JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String destination = "tracking.events." + event.getClass().getSimpleName().toLowerCase();

            // Convert to JSON string for debugging
            String json = objectMapper.writeValueAsString(event);
            logger.debug("Publishing event to {}: {}", destination, json);

            jmsTemplate.convertAndSend(destination, event);

            logger.info("Published event {} to destination {}", event.getEventId(), destination);

        } catch (Exception e) {
            logger.error("Failed to publish event {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}