package com.jjenus.tracker.shared.events;

import com.jjenus.tracker.shared.pubsub.DomainEvent;

import java.time.Instant;

public class AlertRaisedEvent extends DomainEvent {
    private final String alertId;
    private final String ruleKey;
    private final String vehicleId;
    private final String ruleType;
    private final String severity;
    private final String message;
    private final Instant timestamp;
    private final Double latitude;
    private final Double longitude;
    private final Double speed;
    private final String geofenceId;
    
    public AlertRaisedEvent(
        String alertId,
        String ruleKey,
        String vehicleId,
        String ruleType,
        String severity,
        String message,
        Instant timestamp,
        Double latitude,
        Double longitude,
        Double speed,
        String geofenceId
    ) {
        this.alertId = alertId;
        this.ruleKey = ruleKey;
        this.vehicleId = vehicleId;
        this.ruleType = ruleType;
        this.severity = severity;
        this.message = message;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.geofenceId = geofenceId;
    }
    
    public String getAlertId() { return alertId; }
    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getRuleType() { return ruleType; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getSpeed() { return speed; }
    public String getGeofenceId() { return geofenceId; }
    
    @Override
    public String toString() {
        return "AlertRaisedEvent{" +
                "alertId='" + alertId + '\'' +
                ", ruleKey='" + ruleKey + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", ruleType='" + ruleType + '\'' +
                ", severity='" + severity + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
