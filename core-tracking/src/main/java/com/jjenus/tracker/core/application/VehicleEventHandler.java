package com.jjenus.tracker.core.application;

import com.jjenus.tracker.shared.domain.LocationDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(VehicleEventHandler.class);

    private final VehicleCommandService vehicleCommandService;

    public VehicleEventHandler(VehicleCommandService vehicleCommandService) {
        this.vehicleCommandService = vehicleCommandService;
    }

    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "jmsListenerContainerFactory",
            subscription = "vehicle-location-updates")
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        try {
            logger.info("Received location update for device {}", event.getDeviceId());
            logger.debug("Location details: lat={}, lon={}, speed={}, time={}",
                    event.getLocation().latitude(),
                    event.getLocation().longitude(),
                    event.getLocation().speedKmh(),
                    event.getLocation().timestamp());

            // Map device to vehicle
            String vehicleId = "VEH_" + event.getDeviceId();

            vehicleCommandService.updateVehicleLocation(vehicleId, event.getLocation());

            logger.info("Successfully processed location update for vehicle {}", vehicleId);

        } catch (Exception e) {
            logger.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);

            // Re-throw to trigger JMS retry/DLQ
            throw e;
        }
    }
}