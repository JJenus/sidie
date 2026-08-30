package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import java.awt.geom.Path2D;
import java.util.List;

public class GeofenceRule implements IAlertRule {
    public enum Action {
        ENTRY, EXIT, BOTH
    }

    private final String ruleKey;
    private final String ruleName;
    private final String geofenceId;
    private final List<LocationPoint> boundaryPoints;
    private final Action action;
    private final boolean enabled;
    private final int priority;
    private boolean wasInside = false;

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority) {
        this(ruleKey, ruleName, geofenceId, boundaryPoints, action, priority, true);
    }

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority, boolean enabled) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.geofenceId = geofenceId;
        this.boundaryPoints = boundaryPoints;
        this.action = action;
        this.enabled = enabled;
        this.priority = priority;
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled || boundaryPoints == null || boundaryPoints.size() < 3) {
            return null;
        }

        boolean isInside = isPointInPolygon(newLocation);
        AlertDetectedEvent alert = null;

        // Check based on action type
        switch (action) {
            case ENTRY:
                if (!wasInside && isInside) {
                    alert = createEntryAlert(vehicleId, newLocation);
                }
                break;

            case EXIT:
                if (wasInside && !isInside) {
                    alert = createExitAlert(vehicleId, newLocation);
                }
                break;

            case BOTH:
                if (!wasInside && isInside) {
                    alert = createEntryAlert(vehicleId, newLocation);
                } else if (wasInside && !isInside) {
                    alert = createExitAlert(vehicleId, newLocation);
                }
                break;
        }

        wasInside = isInside;
        return alert;
    }

    private AlertDetectedEvent createEntryAlert(String vehicleId, LocationPoint location) {
        String message = String.format(
                "Vehicle %s entered geofence %s at %s",
                vehicleId,
                geofenceId,
                formatCoordinates(location.latitude(), location.longitude())
        );
        return new AlertDetectedEvent(
                ruleKey,
                AlertType.GEOFENCE_ENTRY,
                vehicleId,
                message,
                AlertSeverity.INFO,
                location
        );
    }

    private AlertDetectedEvent createExitAlert(String vehicleId, LocationPoint location) {
        String message = String.format(
                "Vehicle %s exited geofence %s at %s",
                vehicleId,
                geofenceId,
                formatCoordinates(location.latitude(), location.longitude())
        );
        return new AlertDetectedEvent(
                ruleKey,
                AlertType.GEOFENCE_EXIT,
                vehicleId,
                message,
                AlertSeverity.WARNING,
                location
        );
    }

    private String formatCoordinates(double lat, double lon) {
        return String.format("[%.6f, %.6f]", lat, lon);
    }

    private boolean isPointInPolygon(LocationPoint point) {
        try {
            Path2D polygon = new Path2D.Double();
            polygon.moveTo(boundaryPoints.get(0).longitude(),
                    boundaryPoints.get(0).latitude());

            for (int i = 1; i < boundaryPoints.size(); i++) {
                polygon.lineTo(boundaryPoints.get(i).longitude(),
                        boundaryPoints.get(i).latitude());
            }
            polygon.closePath();

            return polygon.contains(point.longitude(), point.latitude());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getRuleKey() { return ruleKey; }

    @Override
    public String getRuleName() { return ruleName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getPriority() { return priority; }

    public boolean isWasInside() { return wasInside; }

    public void setWasInside(boolean wasInside) { this.wasInside = wasInside; }

    // Additional methods
    public String getGeofenceId() { return geofenceId; }
    public Action getAction() { return action; }
    public List<LocationPoint> getBoundaryPoints() { return boundaryPoints; }
}