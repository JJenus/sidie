package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import java.time.Instant;

public class AlertDetectedEvent extends DomainEvent {
    private final String ruleKey;
    private final AlertType alertType;
    private final String vehicleId;
    private final String trackerId;
    private final String message;
    private final AlertSeverity severity;
    private final Instant alertTimestamp;
    private final LocationPoint location;
    
    public AlertDetectedEvent(String ruleKey, AlertType alertType, String vehicleId, String trackerId, String message,
                              AlertSeverity severity, LocationPoint location) {
        this.ruleKey = ruleKey;
        this.alertType = alertType;
        this.vehicleId = vehicleId;
        this.trackerId = trackerId;
        this.message = message;
        this.severity = severity;
        this.alertTimestamp = Instant.now();
        this.location = location;
    }
    
    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getMessage() { return message; }
    public AlertType getAlertType() { return alertType; }

    public AlertSeverity getSeverity() { return severity; }
    public Instant getAlertTimestamp() { return alertTimestamp; }
    public LocationPoint getLocation() { return location; }

    public String getTrackerId() {
        return trackerId;
    }
}
