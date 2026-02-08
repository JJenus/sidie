package com.jjenus.tracker.alerting.application.event;

import com.jjenus.tracker.alerting.api.dto.AlertResponse;
import com.jjenus.tracker.alerting.application.service.AlertService;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.events.AlertRaisedEvent;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
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
    private final EventPublisher eventPublisher;

    public AlertCreationEventHandler(AlertService alertService, EventPublisher eventPublisher) {
        this.alertService = alertService;
        this.eventPublisher = eventPublisher;
    }

    @JmsListener(
            destination = "tracking.events.alertevent",
            containerFactory = "topicJmsListenerContainerFactory"
    )
    public void handleAlertEvent(@Payload AlertDetectedEvent event) {
        try {
            logger.info("Processing alert event: {} for vehicle {}",
                       event.getRuleKey(), event.getVehicleId());

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
            AlertResponse alertResponse = alertService.processAutomatedAlert(
                event.getVehicleId(),
                "system", // trackerId - could be extracted from event if available
                event.getAlertType(),
                event.getSeverity(),
                event.getMessage(),
                metadata
            );

            AlertRaisedEvent alertRaisedEvent = new AlertRaisedEvent(
                    alertResponse.getAlertId().toString(),
                    event.getRuleKey(),
                    alertResponse.getVehicleId(),
                    event.getAlertType().name(),
                    event.getSeverity().name(),
                    event.getMessage(),
                    event.getAlertTimestamp(),
                    alertResponse.getLatitude(),
                    alertResponse.getLongitude(),
                    alertResponse.getSpeedKmh().doubleValue(),
                    metadata
            );

            logger.debug("Alert created from event: {}", event.getRuleKey());

            eventPublisher.publish(alertRaisedEvent);
        } catch (Exception e) {
            logger.error("Failed to process alert event: {}", event.getRuleKey(), e);
        }
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
