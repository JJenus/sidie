package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import java.time.Instant;

public class AlertEvent extends DomainEvent {
    private final String ruleKey;
    private final String vehicleId;
    private final String message;
    private final AlertSeverity severity;
    private final Instant alertTimestamp;
    private final LocationPoint location;
    
    public AlertEvent(String ruleKey, String vehicleId, String message, 
                     AlertSeverity severity, LocationPoint location) {
        this.ruleKey = ruleKey;
        this.vehicleId = vehicleId;
        this.message = message;
        this.severity = severity;
        this.alertTimestamp = Instant.now();
        this.location = location;
    }
    
    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getMessage() { return message; }
    public AlertSeverity getSeverity() { return severity; }
    public Instant getAlertTimestamp() { return alertTimestamp; }
    public LocationPoint getLocation() { return location; }
}
