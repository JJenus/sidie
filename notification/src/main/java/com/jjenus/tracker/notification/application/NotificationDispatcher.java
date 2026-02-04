package com.jjenus.tracker.notification.application;

import com.jjenus.tracker.notification.application.service.*;
import com.jjenus.tracker.notification.domain.entity.Notification;
import com.jjenus.tracker.notification.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationDispatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);
    
    private final Map<NotificationChannel, NotificationService> serviceMap;
    
    public NotificationDispatcher(
        WebSocketNotificationService webSocketService,
        EmailNotificationService emailService,
        SmsNotificationService smsService,
        PushNotificationService pushService
    ) {
        this.serviceMap = new ConcurrentHashMap<>();
        
        // Register all notification services
        serviceMap.put(NotificationChannel.WEBSOCKET, webSocketService);
        serviceMap.put(NotificationChannel.EMAIL, emailService);
        serviceMap.put(NotificationChannel.SMS, smsService);
        serviceMap.put(NotificationChannel.MOBILE_PUSH, pushService);
        serviceMap.put(NotificationChannel.IN_APP, webSocketService); // Use WebSocket for in-app
    }
    
    /**
     * Dispatch notification to appropriate service
     */
    public void dispatch(Notification notification) {
        try {
            NotificationService service = serviceMap.get(notification.getChannel());
            
            if (service == null) {
                logger.error("No service registered for channel: {}", notification.getChannel());
                // Mark as failed
                notification.markAsFailed("No service available for channel: " + notification.getChannel());
                return;
            }
            
            // Send notification
            service.send(notification);
            
            logger.info("Dispatched notification {} via {}", 
                       notification.getNotificationId(), 
                       notification.getChannel());
            
        } catch (Exception e) {
            logger.error("Failed to dispatch notification: {}", notification.getNotificationId(), e);
            notification.markAsFailed(e.getMessage());
        }
    }
    
    /**
     * Get service for a specific channel
     */
    public NotificationService getService(NotificationChannel channel) {
        return serviceMap.get(channel);
    }
}
