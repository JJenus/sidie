package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    
    @Value("${notification.push.enabled:false}")
    private boolean enabled;
    
    @Value("${notification.push.provider:none}")
    private String provider;
    
    @Override
    public void send(Notification notification) {
        if (!enabled) {
            logger.warn("Push notifications are disabled");
            notification.markAsFailed("Push notifications are disabled");
            return;
        }
        
        try {
            notification.markAsSending();
            
            // Implement actual push notification logic here
            // This could use FCM (Firebase), APNS (Apple), etc.
            
            logger.info("Push notification would be sent to: {} via {}", 
                       notification.getRecipient(), provider);
            
            // For now, simulate sending
            Thread.sleep(50); // Simulate network delay
            
            notification.markAsSent();
            
        } catch (Exception e) {
            logger.error("Failed to send push notification", e);
            notification.markAsFailed(e.getMessage());
            throw new RuntimeException("Failed to send push notification", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && !"none".equals(provider);
    }
    
    @Override
    public String getChannel() {
        return "MOBILE_PUSH";
    }
}
