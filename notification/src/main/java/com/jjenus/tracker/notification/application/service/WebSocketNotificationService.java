package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.Notification;
import com.jjenus.tracker.notification.infrastructure.websocket.VehicleTrackingWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);
    
    private final VehicleTrackingWebSocketHandler webSocketHandler;
    
    public WebSocketNotificationService(VehicleTrackingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }
    
    @Override
    public void send(Notification notification) {
        try {
            // Mark as sending
            notification.markAsSending();
            
            // Send via WebSocket
            webSocketHandler.sendAlertNotification(
                notification.getRecipient(), 
                createWebSocketMessage(notification)
            );
            
            // Mark as sent
            notification.markAsSent();
            
            logger.debug("WebSocket notification sent to user: {}", notification.getRecipient());
            
        } catch (Exception e) {
            logger.error("Failed to send WebSocket notification", e);
            notification.markAsFailed(e.getMessage());
            throw new RuntimeException("Failed to send WebSocket notification", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return true; // WebSocket is always available if handler is initialized
    }
    
    @Override
    public String getChannel() {
        return "WEBSOCKET";
    }
    
    /**
     * Create WebSocket message from notification
     */
    private Object createWebSocketMessage(Notification notification) {
        return new WebSocketNotificationMessage(
            notification.getNotificationId(),
            notification.getAlertId(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getChannel().name(),
            System.currentTimeMillis()
        );
    }
    
    // WebSocket message DTO
    private static class WebSocketNotificationMessage {
        private final String notificationId;
        private final String alertId;
        private final String title;
        private final String message;
        private final String channel;
        private final long timestamp;
        
        public WebSocketNotificationMessage(
            String notificationId,
            String alertId,
            String title,
            String message,
            String channel,
            long timestamp
        ) {
            this.notificationId = notificationId;
            this.alertId = alertId;
            this.title = title;
            this.message = message;
            this.channel = channel;
            this.timestamp = timestamp;
        }
        
        public String getNotificationId() { return notificationId; }
        public String getAlertId() { return alertId; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getChannel() { return channel; }
        public long getTimestamp() { return timestamp; }
    }
}
