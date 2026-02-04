package com.jjenus.tracker.alerting.domain.factory;

import com.jjenus.tracker.alerting.application.service.GeofenceService;
import com.jjenus.tracker.alerting.domain.*;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AlertRuleFactory {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleFactory.class);

    private final GeofenceService geofenceService;

    public AlertRuleFactory(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    /**
     * Convert AlertRule entity to appropriate IAlertRule implementation
     */
    public IAlertRule createDomainRule(AlertRule entity, String vehicleId) {
        if (entity == null || !entity.isEnabled()) {
            return null;
        }

        // Check if rule applies to this specific vehicle
        if (!entity.appliesToVehicle(vehicleId)) {
            return null;
        }

        Map<String, Object> params = entity.getParameters();

        try {
            return switch (entity.getRuleType()) {
                case SPEED -> createMaxSpeedRule(entity, params);
                case TIME -> createIdleTimeRule(entity, params);
                case GEOFENCE -> createGeofenceRule(entity, params, vehicleId);
                default -> createGenericRule(entity, params);
            };
        } catch (Exception e) {
            logger.error("Failed to create domain rule for {}: {}",
                    entity.getRuleKey(), e.getMessage());
            return null;
        }
    }

    private MaxSpeedRule createMaxSpeedRule(AlertRule entity, Map<String, Object> params) {
        float speedLimit = getFloatParam(params, "speedLimit", 80.0f);
        return new MaxSpeedRule(entity.getRuleKey(), entity.getRuleName(), speedLimit);
    }

    private IdleTimeRule createIdleTimeRule(AlertRule entity, Map<String, Object> params) {
        int maxIdleMinutes = getIntParam(params, "maxIdleMinutes", 30);
        Duration maxIdleTime = Duration.ofMinutes(maxIdleMinutes);
        return new IdleTimeRule(entity.getRuleKey(), entity.getRuleName(), maxIdleTime);
    }

    private GeofenceRule createGeofenceRule(AlertRule entity, Map<String, Object> params, String vehicleId) {
        String geofenceId = getStringParam(params, "geofenceId", "");
        String actionStr = getStringParam(params, "action", "BOTH");

        // Fetch geofence from service (cached)
        Geofence geofence = getGeofenceById(geofenceId);
        if (geofence == null) {
            logger.warn("Geofence not found: {} for rule {}", geofenceId, entity.getRuleKey());
            return null;
        }

        // Check if this geofence applies to the vehicle
        if (!geofence.hasVehicle(vehicleId)) {
            logger.debug("Geofence {} doesn't apply to vehicle {}", geofenceId, vehicleId);
            return null;
        }

        // Convert boundary points
        List<LocationPoint> boundaryPoints = extractBoundaryPoints(geofence);

        GeofenceRule.Action action;
        try {
            action = GeofenceRule.Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            action = GeofenceRule.Action.BOTH;
        }

        return new GeofenceRule(
                entity.getRuleKey(),
                entity.getRuleName(),
                geofenceId,
                boundaryPoints,
                action,
                entity.getPriority()
        );
    }

    private Geofence getGeofenceById(String geofenceId) {
        try {
            Long id = Long.parseLong(geofenceId);
            return geofenceService.getGeofenceById(id);
        } catch (NumberFormatException e) {
            logger.error("Invalid geofence ID format: {}", geofenceId);
            return null;
        }
    }

    private List<LocationPoint> extractBoundaryPoints(Geofence geofence) {
        if (geofence.getShapeType() == null) {
            return List.of();
        }

        // For circle geofences, create a polygon approximation
        if (geofence.getShapeType().name().equals("CIRCLE") &&
                geofence.getCenterLatitude() != null &&
                geofence.getCenterLongitude() != null &&
                geofence.getRadiusMeters() != null) {

            return createCircleBoundaryPoints(
                    geofence.getCenterLatitude(),
                    geofence.getCenterLongitude(),
                    geofence.getRadiusMeters()
            );
        }

        // For polygon geofences, use the stored points
        return geofence.getPoints().stream()
                .map(point -> new LocationPoint(
                        point.getLatitude(),
                        point.getLongitude(),
                        0.0f, // Speed not relevant for geofence points
                        java.time.Instant.now()
                ))
                .toList();
    }

    private List<LocationPoint> createCircleBoundaryPoints(Double lat, Double lon, Integer radius) {
        // Create 12-point polygon approximation of circle
        List<LocationPoint> points = new java.util.ArrayList<>();
        int pointsCount = 12;

        for (int i = 0; i < pointsCount; i++) {
            double angle = 2 * Math.PI * i / pointsCount;
            double latOffset = (radius / 111000.0) * Math.sin(angle);
            double lonOffset = (radius / (111000.0 * Math.cos(Math.toRadians(lat)))) * Math.cos(angle);

            points.add(new LocationPoint(
                    lat + latOffset,
                    lon + lonOffset,
                    0.0f,
                    java.time.Instant.now()
            ));
        }

        return points;
    }

    private IAlertRule createGenericRule(AlertRule entity, Map<String, Object> params) {
        return new GenericAlertRule(entity, params);
    }

    // Helper methods for parameter extraction
    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        if (params != null && params.containsKey(key)) {
            Object value = params.get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            } else if (value instanceof String) {
                try {
                    return Float.parseFloat((String) value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params != null && params.containsKey(key)) {
            Object value = params.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params != null && params.containsKey(key)) {
            Object value = params.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }
}