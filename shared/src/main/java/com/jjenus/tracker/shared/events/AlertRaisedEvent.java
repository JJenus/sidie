package com.jjenus.tracker.shared.events;

import com.jjenus.tracker.shared.pubsub.DomainEvent;

import java.time.Instant;
import java.util.Map;

public class AlertRaisedEvent extends DomainEvent {
    private final long alertId;
    private final String ruleKey;
    private final String vehicleId;
    private final String alertType;
    private final String severity;
    private final String message;
    private final Instant timestamp;
    private final double latitude;
    private final double longitude;
    private final float speed;
    private final Map<String,Object> metadata;

    public AlertRaisedEvent(
            long alertId,
            String ruleKey,
            String vehicleId,
            String alertType,
            String severity,
            String message,
            Instant timestamp,
            double latitude,
            double longitude,
            float speed,
            Map<String, Object> metadata
    ) {
        this.alertId = alertId;
        this.ruleKey = ruleKey;
        this.vehicleId = vehicleId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.metadata = metadata;
    }
    
    public long getAlertId() { return alertId; }
    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getSpeed() { return speed; }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "AlertRaisedEvent{" +
                "alertId='" + alertId + '\'' +
                ", ruleKey='" + ruleKey + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", ruleType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
