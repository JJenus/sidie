package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.shared.events.LocationDataEvent;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AlertEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventHandler.class);
    
    private final AlertingEngine alertingEngine;
    
    public AlertEventHandler(AlertingEngine alertingEngine) {
        this.alertingEngine = alertingEngine;
    }

    @JmsListener(
            destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListertemis"
    )
    @Transactional
    public void handleVehicleUpdate(@Payload LocationDataEvent event) {
        try {
            logger.debug("Alert Processing received vehicle update for {}", event.getDeviceId());
            logger.debug("Processing vehicle update for {}", event.getEventId());
            
//            alertingEngine.processVehicleUpdate(event.getVehicleId(), event.getNewLocation());
            
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for {}", 
                        event.getEventId(), e);
            throw e;
        }
    }
}