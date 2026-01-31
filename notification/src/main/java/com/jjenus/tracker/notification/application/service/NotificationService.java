package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.Notification;

public interface NotificationService {
    
    /**
     * Send a notification
     */
    void send(Notification notification);
    
    /**
     * Check if service is available
     */
    boolean isAvailable();
    
    /**
     * Get the channel this service handles
     */
    String getChannel();
}
