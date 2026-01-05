package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.core.domain.VehicleUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventHandler.class);
    
    private final AlertingEngine alertingEngine;
    
    public AlertEventHandler(AlertingEngine alertingEngine) {
        this.alertingEngine = alertingEngine;
    }
    
    @JmsListener(destination = "tracking.events.vehicleupdatedevent",
                containerFactory = "jmsListenerContainerFactory",
                subscription = "alert-processing")
    public void handleVehicleUpdate(VehicleUpdatedEvent event) {
        try {
            logger.debug("Processing vehicle update for {}", event.getVehicle().getVehicleId());
            
            alertingEngine.processVehicleUpdate(event.getVehicle(), event.getNewLocation());
            
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for {}", 
                        event.getVehicle().getVehicleId(), e);
            throw e;
        }
    }
}