package com.jjenus.tracker.alerting.domain;

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
    private boolean enabled;
    private final int priority;
    private boolean wasInside = false;

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.geofenceId = geofenceId;
        this.boundaryPoints = boundaryPoints;
        this.action = action;
        this.enabled = true;
        this.priority = priority;
    }

    @Override
    public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled || boundaryPoints == null || boundaryPoints.size() < 3) {
            return null;
        }

        boolean isInside = isPointInPolygon(newLocation);
        AlertEvent alert = null;

        // Check based on action type
        switch (action) {
            case ENTRY:
                if (!wasInside && isInside) {
                    alert = createAlert(vehicleId, newLocation, "entered", AlertSeverity.INFO);
                }
                break;

            case EXIT:
                if (wasInside && !isInside) {
                    alert = createAlert(vehicleId, newLocation, "exited", AlertSeverity.WARNING);
                }
                break;

            case BOTH:
                if (!wasInside && isInside) {
                    alert = createAlert(vehicleId, newLocation, "entered", AlertSeverity.INFO);
                } else if (wasInside && !isInside) {
                    alert = createAlert(vehicleId, newLocation, "exited", AlertSeverity.WARNING);
                }
                break;
        }

        wasInside = isInside;
        return alert;
    }

    private AlertEvent createAlert(String vehicleId, LocationPoint location,
                                   String actionStr, AlertSeverity severity) {
        String message = String.format(
                "Vehicle %s %s geofence %s at %s",
                vehicleId,
                actionStr,
                geofenceId,
                formatCoordinates(location.latitude(), location.longitude())
        );

        return new AlertEvent(
                ruleKey,
                vehicleId,
                message,
                severity,
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
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public int getPriority() { return priority; }

    // Additional methods
    public String getGeofenceId() { return geofenceId; }
    public Action getAction() { return action; }
    public List<LocationPoint> getBoundaryPoints() { return boundaryPoints; }
}