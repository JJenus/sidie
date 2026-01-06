package com.jjenus.tracker.core.application;

import com.jjenus.tracker.shared.domain.LocationDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

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
    public void handleLocationUpdate(LocationDataEvent event) {
        try {
            logger.debug("Processing location update for device {}", event.getDeviceId());
            
            // Map device to vehicle (in production, this would come from a repository)
            String vehicleId = "VEH_" + event.getDeviceId();
            
            vehicleCommandService.updateVehicleLocation(vehicleId, event.getLocation());
            
        } catch (Exception e) {
            logger.error("Failed to process location update for device {}", event.getDeviceId(), e);
            throw e; // Let JMS handle retry/DLQ
        }
    }
}