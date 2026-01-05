package com.jjenus.tracker.shared.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ArtemisEventSubscriber implements EventSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisEventSubscriber.class);
    
    private final ObjectMapper objectMapper;
    private final Map<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlers;
    
    public ArtemisEventSubscriber(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.handlers = new ConcurrentHashMap<>();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        if (eventType == null || handler == null) {
            throw new IllegalArgumentException("Event type and handler cannot be null");
        }
        
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        logger.debug("Registered handler for event type {}", eventType.getSimpleName());
    }
    
    @Override
    public void unsubscribe(Class<? extends DomainEvent> eventType, EventHandler<?> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        
        CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
            logger.debug("Unregistered handler for event type {}", eventType.getSimpleName());
        }
    }
    
    @JmsListener(destination = "tracking.events.*", containerFactory = "jmsListenerContainerFactory")
    public void handleEvent(Message message) {
        try {
            if (!(message instanceof TextMessage textMessage)) {
                logger.warn("Received non-text JMS message");
                return;
            }
            
            String eventJson = textMessage.getText();
            String destination = message.getJMSDestination().toString();
            
            // Extract event type from destination
            String eventTypeName = destination.substring(destination.lastIndexOf('.') + 1);
            eventTypeName = Character.toUpperCase(eventTypeName.charAt(0)) + eventTypeName.substring(1);
            
            // Parse the event
            Class<?> eventClass = Class.forName("com.jjenus.tracker." + eventTypeName);
            DomainEvent event = (DomainEvent) objectMapper.readValue(eventJson, eventClass);
            
            // Get handlers for this event type
            CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventClass);
            if (eventHandlers != null && !eventHandlers.isEmpty()) {
                for (EventHandler handler : eventHandlers) {
                    try {
                        handler.handle(event);
                    } catch (Exception e) {
                        logger.error("Error in event handler for event {}", event.getEventId(), e);
                    }
                }
            }
            
            logger.debug("Processed event {} from destination {}", event.getEventId(), destination);
            
        } catch (Exception e) {
            logger.error("Failed to process JMS message", e);
        }
    }
    
    public Map<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> getHandlers() {
        return Map.copyOf(handlers);
    }
}