package com.jjenus.tracker.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.notification.domain.entity.Notification;
import com.jjenus.tracker.notification.domain.entity.NotificationPreference;
import com.jjenus.tracker.notification.domain.entity.NotificationTemplate;
import com.jjenus.tracker.notification.domain.enums.DeliveryStatus;
import com.jjenus.tracker.notification.domain.enums.NotificationChannel;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import com.jjenus.tracker.shared.events.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class NotificationOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);
    
    private final NotificationDispatcher dispatcher;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    
    public NotificationOrchestrator(
        NotificationDispatcher dispatcher,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository,
        ObjectMapper objectMapper
    ) {
        this.dispatcher = dispatcher;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process alert and create notifications for subscribed users
     */
    public void processAlert(AlertRaisedEvent alert) {
        try {
            logger.info("Processing alert for notification: {}", alert.getAlertId());
            
            // Find users who should receive this alert
            List<String> recipients = findRecipientsForAlert(alert);
            logger.debug("Found {} recipients for alert {}", recipients.size(), alert.getAlertId());
            
            // Create notifications for each recipient
            for (String recipient : recipients) {
                try {
                    createNotificationsForRecipient(alert, recipient);
                } catch (Exception e) {
                    logger.error("Failed to create notifications for recipient: {}", recipient, e);
                }
            }
            
            logger.info("Successfully processed alert {}", alert.getAlertId());
            
        } catch (Exception e) {
            logger.error("Failed to process alert: {}", alert.getAlertId(), e);
            throw new RuntimeException("Failed to process alert for notification", e);
        }
    }
    
    /**
     * Find recipients for a specific alert
     */
    private List<String> findRecipientsForAlert(AlertRaisedEvent alert) {
        // In a real implementation, this would query:
        // 1. Users assigned to the vehicle
        // 2. Users with permission for this alert type
        // 3. Users who have not muted this alert
        
        // For now, return a dummy list - replace with actual user service
        return List.of("user1", "user2", "admin");
    }
    
    /**
     * Create notifications for a specific recipient based on their preferences
     */
    private void createNotificationsForRecipient(AlertRaisedEvent alert, String recipient) {
        // Get user's preferences for this alert type
        List<NotificationPreference> preferences = preferenceRepository
            .findByUserIdAndAlertType(recipient, alert.getAlertType());
        
        if (preferences.isEmpty()) {
            // Use default preferences
            preferences = getDefaultPreferences(recipient, alert.getAlertType());
        }
        
        // Create notifications for each enabled channel
        for (NotificationPreference preference : preferences) {
            if (preference.isEnabled()) {
                for (NotificationChannel channel : preference.getEnabledChannels()) {
                    createAndQueueNotification(alert, recipient, channel);
                }
            }
        }
    }
    
    /**
     * Create and queue a single notification
     */
    private void createAndQueueNotification(
        AlertRaisedEvent alert, 
        String recipient, 
        NotificationChannel channel
    ) {
        try {
            // Find appropriate template
            NotificationTemplate template = findTemplate(alert.getAlertType(), channel);
            if (template == null || !template.isEnabled()) {
                logger.warn("No enabled template found for rule type {} and channel {}", 
                          alert.getAlertType(), channel);
                return;
            }
            
            // Create notification entity
            Notification notification = new Notification();
            notification.setAlertId(alert.getAlertId());
            notification.setChannel(channel);
            notification.setRecipient(recipient);
            notification.setTemplateId(template.getTemplateId());
            notification.setTemplateVariables(formatTemplateVariables(alert));
            notification.setTitle(renderTemplate(template.getSubjectTemplate(), alert));
            notification.setMessage(renderTemplate(template.getBodyTemplate(), alert));
            notification.setStatus(DeliveryStatus.PENDING);
            
            // Save notification (this will generate notificationId)
            // In actual implementation, you would save to repository
            // For now, we'll dispatch immediately
            
            // Dispatch notification
            dispatcher.dispatch(notification);
            
            logger.info("Queued notification for alert {} to {} via {}", 
                       alert.getAlertId(), recipient, channel);
            
        } catch (Exception e) {
            logger.error("Failed to create notification for recipient {} via channel {}", 
                        recipient, channel, e);
        }
    }
    
    /**
     * Find template for alert type and channel
     */
    private NotificationTemplate findTemplate(String ruleType, NotificationChannel channel) {
        return templateRepository
            .findByTemplateTypeAndChannelAndEnabledTrue(ruleType, channel)
            .stream()
            .findFirst()
            .orElseGet(() -> getDefaultTemplate(channel));
    }
    
    /**
     * Get default template for channel
     */
    private NotificationTemplate getDefaultTemplate(NotificationChannel channel) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId("DEFAULT_" + channel.name());
        template.setName("Default " + channel.getDisplayName() + " Template");
        template.setTemplateType("DEFAULT");
        template.setChannel(channel);
        template.setSubjectTemplate("Alert: {{alertType}}");
        template.setBodyTemplate("{{message}}\\n\\nVehicle: {{vehicleId}}\\nTime: {{timestamp}}");
        return template;
    }
    
    /**
     * Format template variables from alert
     */
    private String formatTemplateVariables(AlertRaisedEvent alert) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("alertId", alert.getAlertId());
            variables.put("ruleKey", alert.getRuleKey());
            variables.put("vehicleId", alert.getVehicleId());
            variables.put("alertType", alert.getAlertType());
            variables.put("severity", alert.getSeverity());
            variables.put("message", alert.getMessage());
            variables.put("timestamp", alert.getTimestamp().toString());
            variables.put("latitude", alert.getLatitude());
            variables.put("longitude", alert.getLongitude());
            
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            logger.error("Failed to format template variables", e);
            return "{}";
        }
    }
    
    /**
     * Render template with variables
     */
    private String renderTemplate(String template, AlertRaisedEvent alert) {
        if (template == null) return "";
        
        return template
            .replace("{{alertId}}", alert.getAlertId()+"")
            .replace("{{ruleKey}}", alert.getRuleKey())
            .replace("{{vehicleId}}", alert.getVehicleId())
            .replace("{{alertType}}", alert.getAlertType())
            .replace("{{severity}}", alert.getSeverity())
            .replace("{{message}}", alert.getMessage())
            .replace("{{timestamp}}", alert.getTimestamp().toString())
            .replace("{{latitude}}", String.valueOf(alert.getLatitude()))
            .replace("{{longitude}}", String.valueOf(alert.getLongitude()));
    }
    
    /**
     * Get default preferences for a user
     */
    private List<NotificationPreference> getDefaultPreferences(String userId, String alertType) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setAlertType(alertType);
        preference.setEnabled(true);
        
        // Default channels based on alert severity
        String severity = "MEDIUM"; // Would come from alert
        Set<NotificationChannel> channels = new HashSet<>();
        
        if ("CRITICAL".equals(severity)) {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.SMS);
            channels.add(NotificationChannel.MOBILE_PUSH);
        } else if ("HIGH".equals(severity)) {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.MOBILE_PUSH);
        } else {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.EMAIL);
        }
        
        preference.setEnabledChannels(channels);
        return List.of(preference);
    }
}
