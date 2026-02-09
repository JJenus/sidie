package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import java.time.Duration;
import java.time.Instant;

public class IdleTimeRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final Duration maxIdleTime;
    private boolean enabled;
    private final int priority;

    private final java.util.Map<String, Instant> lastMovementTimes = new java.util.HashMap<>();

    public IdleTimeRule(String ruleKey, String ruleName, Duration maxIdleTime) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.maxIdleTime = maxIdleTime;
        this.enabled = true;
        this.priority = 1;
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled) return null;

        Instant now = Instant.now();
        Instant lastMovement = lastMovementTimes.get(vehicleId);

        // Update last movement time if vehicle is moving
        if (newLocation.speedKmh() > 1.0) {
            lastMovementTimes.put(vehicleId, now);
            return null;
        }

        // If we have no last movement time, set it and return
        if (lastMovement == null) {
            lastMovementTimes.put(vehicleId, now);
            return null;
        }

        Duration idleDuration = Duration.between(lastMovement, now);

        if (idleDuration.compareTo(maxIdleTime) > 0) {
            String message = String.format(
                    "Vehicle %s has been idle for %d minutes (max allowed: %d minutes) at %s",
                    vehicleId,
                    idleDuration.toMinutes(),
                    maxIdleTime.toMinutes(),
                    formatCoordinates(newLocation.latitude(), newLocation.longitude())
            );

            return new AlertDetectedEvent(
                    ruleKey,
                    AlertType.IDLE_TIMEOUT,
                    vehicleId,
                    "tracker_id_idle_rule",
                    message,
                    AlertSeverity.INFO,
                    newLocation
            );
        }
        return null;
    }

    private String formatCoordinates(double lat, double lon) {
        return String.format("[%.6f, %.6f]", lat, lon);
    }

    @Override
    public String getRuleKey() { return ruleKey; }

    @Override
    public String getRuleName() { return ruleName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public int getPriority() { return priority; }

    public Duration getMaxIdleTime() { return maxIdleTime; }
}