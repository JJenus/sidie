package com.jjenus.tracker.alerting.application.event;

import com.jjenus.tracker.alerting.application.service.AlertService;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AlertCreationEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlertCreationEventHandler.class);

    private final AlertService alertService;

    public AlertCreationEventHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    @JmsListener(
            destination = "tracking.events.alertevent",
            containerFactory = "topicJmsListenerContainerFactory"
    )
    public void handleAlertEvent(@Payload AlertDetectedEvent event) {
        try {
            logger.info("Processing alert event: {} for vehicle {}",
                       event.getRuleKey(), event.getVehicleId());

            // Map rule key to alert type
            AlertType alertType = mapRuleKeyToAlertType(event.getRuleKey());

            // Create metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ruleKey", event.getRuleKey());
            metadata.put("alertTimestamp", event.getAlertTimestamp());

            if (event.getLocation() != null) {
                metadata.put("latitude", event.getLocation().latitude());
                metadata.put("longitude", event.getLocation().longitude());
                metadata.put("speedKmh", event.getLocation().speedKmh());
            }

            // Create the alert
            alertService.processAutomatedAlert(
                event.getVehicleId(),
                "system", // trackerId - could be extracted from event if available
                alertType,
                event.getSeverity(),
                event.getMessage(),
                metadata
            );

            logger.debug("Alert created from event: {}", event.getRuleKey());

        } catch (Exception e) {
            logger.error("Failed to process alert event: {}", event.getRuleKey(), e);
        }
    }

    private AlertType mapRuleKeyToAlertType(String ruleKey) {
        if (ruleKey == null) {
            return AlertType.UNKNOWN;
        }

        // Map common rule patterns to alert types
        if (ruleKey.contains("speed") || ruleKey.contains("overspeed")) {
            return AlertType.OVERSPEED;
        } else if (ruleKey.contains("idle") || ruleKey.contains("timeout")) {
            return AlertType.IDLE_TIMEOUT;
        } else if (ruleKey.contains("geofence") || ruleKey.contains("fence")) {
            return AlertType.GEOFENCE_VIOLATION;
        } else if (ruleKey.contains("battery")) {
            return AlertType.LOW_BATTERY;
        } else if (ruleKey.contains("disconnect")) {
            return AlertType.DEVICE_DISCONNECTED;
        } else if (ruleKey.contains("tamper")) {
            return AlertType.TAMPER_DETECTED;
        } else if (ruleKey.contains("panic")) {
            return AlertType.PANIC_BUTTON_PRESSED;
        }

        return AlertType.UNKNOWN;
    }

    // Additional event handlers for other alert-related events
    @JmsListener(
            destination = "tracking.events.vehicleupdatedevent",
            containerFactory = "topicJmsListenerContainerFactory"
    )
    public void handleVehicleUpdateForAlertCheck(@Payload com.jjenus.tracker.shared.events.VehicleUpdatedEvent event) {
        // This could trigger additional alert checks based on vehicle updates
        // For example, check for no-movement alerts, etc.
        logger.debug("Vehicle update received for alert checking: {}", event.getVehicleId());
    }
}
