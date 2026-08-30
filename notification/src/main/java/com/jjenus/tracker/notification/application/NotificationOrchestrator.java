package com.jjenus.tracker.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.notification.domain.entity.Notification;
import com.jjenus.tracker.notification.domain.entity.NotificationPreference;
import com.jjenus.tracker.notification.domain.entity.NotificationTemplate;
import com.jjenus.tracker.notification.domain.enums.DeliveryStatus;
import com.jjenus.tracker.notification.domain.enums.NotificationChannel;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import com.jjenus.tracker.shared.events.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationDispatcher dispatcher;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public NotificationOrchestrator(
        NotificationDispatcher dispatcher,
        NotificationRepository notificationRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository,
        ObjectMapper objectMapper
    ) {
        this.dispatcher = dispatcher;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    public void processAlert(AlertRaisedEvent alert) {
        try {
            logger.info("Processing alert for notification: {}", alert.getAlertId());

            List<String> recipients = findRecipientsForAlert(alert);
            logger.debug("Found {} recipients for alert {}", recipients.size(), alert.getAlertId());

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

    private List<String> findRecipientsForAlert(AlertRaisedEvent alert) {
        List<NotificationPreference> preferences = preferenceRepository
                .findByAlertType(alert.getAlertType());

        if (preferences.isEmpty()) {
            return List.of("admin");
        }

        return preferences.stream()
                .filter(NotificationPreference::isEnabled)
                .map(NotificationPreference::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    private void createNotificationsForRecipient(AlertRaisedEvent alert, String recipient) {
        List<NotificationPreference> preferences = preferenceRepository
            .findByUserIdAndAlertType(recipient, alert.getAlertType());

        if (preferences.isEmpty()) {
            preferences = getDefaultPreferences(recipient, alert.getAlertType(), alert.getSeverity());
        }

        for (NotificationPreference preference : preferences) {
            if (preference.isEnabled()) {
                for (NotificationChannel channel : preference.getEnabledChannels()) {
                    createAndQueueNotification(alert, recipient, channel);
                }
            }
        }
    }

    private void createAndQueueNotification(
        AlertRaisedEvent alert,
        String recipient,
        NotificationChannel channel
    ) {
        try {
            NotificationTemplate template = findTemplate(alert.getAlertType(), channel);
            if (template == null || !template.isEnabled()) {
                logger.warn("No enabled template found for rule type {} and channel {}",
                          alert.getAlertType(), channel);
                return;
            }

            Notification notification = new Notification();
            notification.setAlertId(alert.getAlertId());
            notification.setChannel(channel);
            notification.setRecipient(recipient);
            notification.setTemplateId(template.getTemplateId());
            notification.setTemplateVariables(formatTemplateVariables(alert));
            notification.setTitle(renderTemplate(template.getSubjectTemplate(), alert));
            notification.setMessage(renderTemplate(template.getBodyTemplate(), alert));
            notification.setStatus(DeliveryStatus.PENDING);

            notificationRepository.save(notification);
            logger.debug("Persisted notification {} for alert {} to {} via {}",
                    notification.getNotificationId(), alert.getAlertId(), recipient, channel);

            dispatcher.dispatch(notification);

        } catch (Exception e) {
            logger.error("Failed to create notification for recipient {} via channel {}",
                        recipient, channel, e);
        }
    }

    private NotificationTemplate findTemplate(String ruleType, NotificationChannel channel) {
        return templateRepository
            .findByTemplateTypeAndChannelAndEnabledTrue(ruleType, channel)
            .stream()
            .findFirst()
            .orElseGet(() -> getDefaultTemplate(channel));
    }

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

    private String renderTemplate(String template, AlertRaisedEvent alert) {
        if (template == null) return "";

        return template
            .replace("{{alertId}}", nullSafe(alert.getAlertId()))
            .replace("{{ruleKey}}", nullSafe(alert.getRuleKey()))
            .replace("{{vehicleId}}", nullSafe(alert.getVehicleId()))
            .replace("{{alertType}}", nullSafe(alert.getAlertType()))
            .replace("{{severity}}", nullSafe(alert.getSeverity()))
            .replace("{{message}}", nullSafe(alert.getMessage()))
            .replace("{{timestamp}}", alert.getTimestamp() != null ? alert.getTimestamp().toString() : "")
            .replace("{{latitude}}", alert.getLatitude() != null ? String.valueOf(alert.getLatitude()) : "")
            .replace("{{longitude}}", alert.getLongitude() != null ? String.valueOf(alert.getLongitude()) : "");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private List<NotificationPreference> getDefaultPreferences(String userId, String alertType, String severity) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setAlertType(alertType);
        preference.setEnabled(true);

        Set<NotificationChannel> channels = new HashSet<>();
        if ("CRITICAL".equals(severity)) {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.SMS);
            channels.add(NotificationChannel.MOBILE_PUSH);
            channels.add(NotificationChannel.EMAIL);
        } else if ("HIGH".equals(severity)) {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.MOBILE_PUSH);
            channels.add(NotificationChannel.EMAIL);
        } else {
            channels.add(NotificationChannel.WEBSOCKET);
            channels.add(NotificationChannel.EMAIL);
        }

        preference.setEnabledChannels(channels);
        return List.of(preference);
    }
}
