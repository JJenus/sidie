package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.application.service.VehicleService;
import com.jjenus.tracker.shared.events.LocationDataEvent;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(VehicleEventHandler.class);

    private final VehicleService vehicleService;
    private final EventPublisher eventPublisher;

    public VehicleEventHandler(VehicleService vehicleService, EventPublisher eventPublisher) {
        this.vehicleService = vehicleService;
        this.eventPublisher = eventPublisher;
    }

    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        try {
            logger.info("Received location update for device {}", event.getDeviceId());

            // Find or create vehicle for this device
            String vehicleId = vehicleService.findVehicleIdForDevice(event.getDeviceId());

            // Update vehicle location
//            vehicleService.updateVehicleLocation(vehicleId, event.getLocation());

            // Publish vehicle update event for alerting
            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(vehicleId, event.getLocation());
            eventPublisher.publish(vehicleUpdatedEvent);

            logger.info("Updated vehicle {} for device {}", vehicleId, event.getDeviceId());

        } catch (Exception e) {
            logger.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);
            throw e;
        }
    }
}