package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationService.class);
    
    @Value("${notification.sms.enabled:false}")
    private boolean enabled;
    
    @Value("${notification.sms.provider:none}")
    private String provider;
    
    @Override
    public void send(Notification notification) {
        if (!enabled) {
            logger.warn("SMS notifications are disabled");
            notification.markAsFailed("SMS notifications are disabled");
            return;
        }
        
        try {
            notification.markAsSending();
            
            // Implement actual SMS sending logic here
            // This could use Twilio, AWS SNS, etc.
            
            logger.info("SMS would be sent to: {} via {}", 
                       notification.getRecipient(), provider);
            
            // For now, simulate sending
            Thread.sleep(100); // Simulate network delay
            
            notification.markAsSent();
            
        } catch (Exception e) {
            logger.error("Failed to send SMS notification", e);
            notification.markAsFailed(e.getMessage());
            throw new RuntimeException("Failed to send SMS notification", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && !"none".equals(provider);
    }
    
    @Override
    public String getChannel() {
        return "SMS";
    }
}
