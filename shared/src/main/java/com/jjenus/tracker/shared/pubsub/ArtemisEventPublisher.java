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
            // Use event type as destination name for topic-based routing
            String destination = "tracking.events." + event.getClass().getSimpleName().toLowerCase();
            
            jmsTemplate.convertAndSend(destination, event);
            
            logger.debug("Published event {} to destination {}", event.getEventId(), destination);
            
        } catch (Exception e) {
            logger.error("Failed to publish event {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}