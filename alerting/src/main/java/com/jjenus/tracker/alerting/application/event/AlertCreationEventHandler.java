package com.jjenus.tracker.alerting.application.event;

import com.jjenus.tracker.alerting.application.AlertingEngine;
import com.jjenus.tracker.alerting.application.service.AlertService;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.shared.events.AlertRaisedEvent;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
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
    private final AlertingEngine alertingEngine;
    private final EventPublisher eventPublisher;

    public AlertCreationEventHandler(
            AlertService alertService,
            AlertingEngine alertingEngine,
            EventPublisher eventPublisher) {
        this.alertService = alertService;
        this.alertingEngine = alertingEngine;
        this.eventPublisher = eventPublisher;
    }

    @JmsListener(
            destination = "tracking.events.alertdetectedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1-3"
    )
    public void handleAlertEvent(@Payload AlertDetectedEvent event) {
        try {
            logger.info("Processing alert event: {} for vehicle {}",
                       event.getRuleKey(), event.getVehicleId());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ruleKey", event.getRuleKey());
            metadata.put("alertTimestamp", event.getAlertTimestamp());

            if (event.getLocation() != null) {
                metadata.put("latitude", event.getLocation().latitude());
                metadata.put("longitude", event.getLocation().longitude());
                metadata.put("speedKmh", event.getLocation().speedKmh());
            }

            alertService.processAutomatedAlert(
                event.getVehicleId(),
                "system",
                event.getAlertType(),
                event.getSeverity(),
                event.getMessage(),
                metadata
            );

            AlertRaisedEvent alertRaisedEvent = new AlertRaisedEvent(
                    event.getRuleKey(),
                    event.getRuleKey(),
                    event.getVehicleId(),
                    event.getAlertType().name(),
                    event.getSeverity().name(),
                    event.getMessage(),
                    event.getAlertTimestamp(),
                    event.getLocation() != null ? event.getLocation().latitude() : null,
                    event.getLocation() != null ? event.getLocation().longitude() : null,
                    event.getLocation() != null ? (double) event.getLocation().speedKmh() : null,
                    metadata
            );

            logger.debug("Alert event published: {}", event.getRuleKey());
            eventPublisher.publish(alertRaisedEvent);
        } catch (Exception e) {
            logger.error("Failed to process alert event: {}", event.getRuleKey(), e);
        }
    }

    @JmsListener(
            destination = "tracking.events.vehicleupdatedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1-5"
    )
    public void handleVehicleUpdateForAlertCheck(@Payload VehicleUpdatedEvent event) {
        try {
            logger.debug("Vehicle update received for alert checking: {}", event.getVehicleId());
            alertingEngine.processVehicleUpdate(event.getVehicleId(), event.getNewLocation());
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for alerting: {}", event.getVehicleId(), e);
        }
    }
}
