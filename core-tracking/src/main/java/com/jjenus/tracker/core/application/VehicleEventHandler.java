package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.application.service.VehicleService;
import com.jjenus.tracker.core.domain.FuelCutRequestedEvent;
import com.jjenus.tracker.shared.events.LocationDataEvent;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
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

    public VehicleEventHandler(
            VehicleService vehicleService,
            EventPublisher eventPublisher) {
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

            String vehicleId = vehicleService.findVehicleIdForDevice(event.getDeviceId());

            vehicleService.updateVehicleLocation(vehicleId, event.getLocation());

            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(
                    vehicleId,
                    event.getLocation(),
                    event.getMetaData()
            );
            eventPublisher.publish(vehicleUpdatedEvent);

            logger.debug("Updated vehicle {} for device {}", vehicleId, event.getDeviceId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Skipping location update for device {}: {}",
                    event != null ? event.getDeviceId() : "unknown", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);
            throw e;
        }
    }

    @JmsListener(destination = "tracking.events.fuelcutrequestedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    public void handleFuelCutRequest(@Payload FuelCutRequestedEvent event) {
        try {
            logger.info("Received fuel cut request for vehicle {}", event.getVehicleId());
            eventPublisher.publish(event);
        } catch (Exception e) {
            logger.error("Failed to forward fuel cut request for vehicle {}",
                    event != null ? event.getVehicleId() : "unknown", e);
        }
    }
}
