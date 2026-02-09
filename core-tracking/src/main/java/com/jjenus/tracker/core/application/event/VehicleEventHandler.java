package com.jjenus.tracker.core.application.event;

import com.jjenus.tracker.core.application.service.LocationCommandService;
import com.jjenus.tracker.core.application.service.TripCommandService;
import com.jjenus.tracker.core.application.service.VehicleCommandService;
import com.jjenus.tracker.core.application.service.VehicleQueryService;
import com.jjenus.tracker.shared.events.LocationDataEvent;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleEventHandler {

    private final VehicleQueryService vehicleQueryService;
    private final LocationCommandService locationCommandService;
    private final EventPublisher eventPublisher;

    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        try {
            log.info("Received location update for device {}", event.getDeviceId());

            // Get vehicle ID for this device
            String vehicleId = null;
            try {
                var vehicleResponse = vehicleQueryService.getVehicleByDeviceId(event.getDeviceId());
                vehicleId = vehicleResponse.getVehicleId();
            } catch (Exception e) {
                log.warn("No vehicle found for device {}, creating new vehicle", event.getDeviceId());
            }

            // Record location
            if (vehicleId != null) {
                locationCommandService.recordLocation(
                        event.getDeviceId(), // tracker ID
                        event.getLocation().latitude(),
                        event.getLocation().longitude(),
                        event.getLocation().speedKmh(),
                        event.getLocation().timestamp(),
                        event.getLocation().metadata()
                );
            }

            // Publish vehicle update event for alerting
            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(
                    vehicleId,
                    event.getLocation(),
                    event.getDeviceId() // trackerId
            );
            eventPublisher.publish(vehicleUpdatedEvent);

            log.info("Updated vehicle {} for device {}", vehicleId, event.getDeviceId());

        } catch (Exception e) {
            log.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);
            throw e;
        }
    }
}