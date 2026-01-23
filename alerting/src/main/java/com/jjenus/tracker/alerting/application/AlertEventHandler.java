package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AlertEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventHandler.class);
    
    private final AlertingEngine alertingEngine;
    
    public AlertEventHandler(AlertingEngine alertingEngine) {
        this.alertingEngine = alertingEngine;
    }

    @JmsListener(
            destination = "tracking.events.vehicleupdatedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            subscription = "alert-processing"
    )
    public void handleVehicleUpdate(@Payload VehicleUpdatedEvent event) {
        try {
            logger.debug("Alert Processing received vehicle update for {}", event.getVehicleId());
            logger.debug("Processing vehicle update for {}", event.getVehicleId());
            
            alertingEngine.processVehicleUpdate(event.getVehicleId(), event.getNewLocation());
            
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for {}", 
                        event.getVehicleId(), e);
            throw e;
        }
    }
}