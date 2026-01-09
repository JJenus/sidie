package com.jjenus.tracker.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.core.domain.entity.*;
import com.jjenus.tracker.core.infrastructure.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertService {
    
    private final TrackerAlertRepository alertRepository;
    private final AlertRuleRepository ruleRepository;
    private final VehicleRepository vehicleRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    
    // Cache for rule cooldowns
    private final Map<String, Instant> ruleCooldownCache = new HashMap<>();
    
    public AlertService(TrackerAlertRepository alertRepository,
                       AlertRuleRepository ruleRepository,
                       VehicleRepository vehicleRepository,
                       TrackerRepository trackerRepository,
                       TrackerLocationRepository locationRepository,
                       ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.ruleRepository = ruleRepository;
        this.vehicleRepository = vehicleRepository;
        this.trackerRepository = trackerRepository;
        this.locationRepository = locationRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public TrackerAlert createAlert(String vehicleId, String alertType, 
                                   String message, AlertSeverity severity,
                                   Map<String, Object> metadata) {
        
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));
        
        TrackerAlert alert = new TrackerAlert();
        alert.setVehicle(vehicle);
        alert.setTracker(vehicle.getDeviceId() != null ? 
            trackerRepository.findByDeviceId(vehicle.getDeviceId()).orElse(null) : null);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setLocation(vehicle.getCurrentLocation());
        
        if (metadata != null && !metadata.isEmpty()) {
            try {
                alert.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                // Log error but continue
                System.err.println("Failed to serialize metadata: " + e.getMessage());
            }
        }
        
        TrackerAlert savedAlert = alertRepository.save(alert);
        
        // Trigger notification based on severity
        triggerNotifications(savedAlert);
        
        return savedAlert;
    }
    
    @Transactional
    public void checkAndCreateAlerts(Vehicle vehicle, TrackerLocation location, 
                                    Map<String, Object> deviceStatus) {
        
        List<AlertRule> activeRules = ruleRepository.findActiveRulesOrderedByPriority();
        
        for (AlertRule rule : activeRules) {
            // Check cooldown
            if (isRuleInCooldown(rule, vehicle.getVehicleId())) {
                continue;
            }
            
            boolean shouldTrigger = evaluateRule(rule, vehicle, location, deviceStatus);
            
            if (shouldTrigger) {
                TrackerAlert alert = createAlertFromRule(rule, vehicle, location);
                alertRepository.save(alert);
                
                // Update cooldown cache
                ruleCooldownCache.put(
                    getRuleCooldownKey(rule.getRuleId(), vehicle.getVehicleId()),
                    Instant.now()
                );
                
                // Execute rule actions
                executeRuleActions(rule, alert);
            }
        }
    }
    
    private boolean evaluateRule(AlertRule rule, Vehicle vehicle, 
                                TrackerLocation location, Map<String, Object> deviceStatus) {
        
        try {
            Map<String, Object> params = objectMapper.readValue(
                rule.getParameters(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            
            switch (rule.getRuleType()) {
                case SPEED:
                    return evaluateSpeedRule(params, location);
                    
                case GEOFENCE:
                    return evaluateGeofenceRule(params, vehicle, location);
                    
                case TIME:
                    return evaluateTimeRule(params, vehicle, location);
                    
                case DEVICE_STATUS:
                    return evaluateDeviceStatusRule(params, deviceStatus);
                    
                case VEHICLE_STATUS:
                    return evaluateVehicleStatusRule(params, vehicle);
                    
                default:
                    return false;
            }
            
        } catch (Exception e) {
            System.err.println("Failed to evaluate rule " + rule.getRuleKey() + ": " + e.getMessage());
            return false;
        }
    }
    
    private boolean evaluateSpeedRule(Map<String, Object> params, TrackerLocation location) {
        if (location.getSpeedKmh() == null) return false;
        
        Float speedLimit = ((Number) params.get("speedLimit")).floatValue();
        Float buffer = params.containsKey("buffer") ? 
            ((Number) params.get("buffer")).floatValue() : 0;
        
        return location.getSpeedKmh() > (speedLimit + buffer);
    }
    
    private boolean evaluateGeofenceRule(Map<String, Object> params, 
                                        Vehicle vehicle, TrackerLocation location) {
        // Implementation would check against geofences
        return false; // Placeholder
    }
    
    private boolean evaluateTimeRule(Map<String, Object> params, 
                                    Vehicle vehicle, TrackerLocation location) {
        if (!Boolean.TRUE.equals(vehicle.getAccStatus())) {
            return false; // Engine/ACC is off
        }
        
        Integer maxIdleMinutes = (Integer) params.get("maxIdleMinutes");
        if (maxIdleMinutes != null && vehicle.getLastAccOffTime() != null) {
            Duration idleDuration = Duration.between(
                vehicle.getLastAccOffTime(),
                Instant.now()
            );
            return idleDuration.toMinutes() > maxIdleMinutes;
        }
        
        return false;
    }
    
    private boolean evaluateDeviceStatusRule(Map<String, Object> params, 
                                            Map<String, Object> deviceStatus) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String statusKey = entry.getKey();
            Object expectedValue = entry.getValue();
            
            if (deviceStatus.containsKey(statusKey) && 
                Objects.equals(deviceStatus.get(statusKey), expectedValue)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean evaluateVehicleStatusRule(Map<String, Object> params, Vehicle vehicle) {
        // Check vehicle-specific conditions
        return false; // Placeholder
    }
    
    private TrackerAlert createAlertFromRule(AlertRule rule, Vehicle vehicle, 
                                            TrackerLocation location) {
        
        TrackerAlert alert = new TrackerAlert();
        alert.setVehicle(vehicle);
        alert.setTracker(vehicle.getDeviceId() != null ? 
            trackerRepository.findByDeviceId(vehicle.getDeviceId()).orElse(null) : null);
        alert.setAlertType(rule.getRuleName());
        alert.setSeverity(determineSeverityFromRule(rule));
        alert.setMessage(generateAlertMessage(rule, vehicle, location));
        alert.setLocation(location);
        
        // Add rule metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ruleId", rule.getRuleId());
        metadata.put("ruleKey", rule.getRuleKey());
        metadata.put("ruleType", rule.getRuleType().name());
        
        try {
            alert.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            // Ignore serialization error
        }
        
        return alert;
    }
    
    private AlertSeverity determineSeverityFromRule(AlertRule rule) {
        try {
            Map<String, Object> params = objectMapper.readValue(
                rule.getParameters(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            
            if (params.containsKey("severity")) {
                return AlertSeverity.valueOf(params.get("severity").toString().toUpperCase());
            }
        } catch (Exception e) {
            // Use default
        }
        
        // Default based on rule type
        return switch (rule.getRuleType()) {
            case SPEED, SAFETY -> AlertSeverity.CRITICAL;
            case GEOFENCE, VEHICLE_STATUS -> AlertSeverity.WARNING;
            default -> AlertSeverity.INFO;
        };
    }
    
    private String generateAlertMessage(AlertRule rule, Vehicle vehicle, 
                                       TrackerLocation location) {
        
        String vehicleId = vehicle.getVehicleId();
        String licensePlate = vehicle.getLicensePlate() != null ? 
            " (" + vehicle.getLicensePlate() + ")" : "";
        
        return switch (rule.getRuleType()) {
            case SPEED -> String.format(
                "Vehicle %s%s exceeded speed limit. Current speed: %.1f km/h at %s",
                vehicleId, licensePlate, location.getSpeedKmh(), location.getRecordedAt()
            );
            case TIME -> String.format(
                "Vehicle %s%s has been idle for too long at %s",
                vehicleId, licensePlate, location.getRecordedAt()
            );
            case GEOFENCE -> String.format(
                "Vehicle %s%s violated geofence at %s",
                vehicleId, licensePlate, location.getRecordedAt()
            );
            default -> String.format(
                "Alert triggered for vehicle %s%s: %s",
                vehicleId, licensePlate, rule.getRuleName()
            );
        };
    }
    
    private void executeRuleActions(AlertRule rule, TrackerAlert alert) {
        try {
            if (rule.getActions() != null) {
                Map<String, Object> actions = objectMapper.readValue(
                    rule.getActions(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );
                
                // Execute actions based on configuration
                if (actions.containsKey("sendNotification")) {
                    sendNotification(alert, actions.get("sendNotification"));
                }
                
                if (actions.containsKey("executeCommand") && 
                    Boolean.TRUE.equals(actions.get("executeCommand"))) {
                    executeCommand(alert);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to execute rule actions: " + e.getMessage());
        }
    }
    
    private void sendNotification(TrackerAlert alert, Object notificationConfig) {
        // Implementation for sending notifications
        // Could be email, SMS, push notification, etc.
        System.out.println("Alert notification: " + alert.getMessage());
    }
    
    private void executeCommand(TrackerAlert alert) {
        // Execute commands like fuel cut for critical alerts
        if (alert.isCritical() && !alert.getVehicle().getFuelCutActive()) {
            // Could trigger fuel cut command
            System.out.println("Executing command for alert: " + alert.getAlertId());
        }
    }
    
    private void triggerNotifications(TrackerAlert alert) {
        // Determine notification channels based on severity
        List<String> channels = new ArrayList<>();
        
        switch (alert.getSeverity()) {
            case CRITICAL:
                channels.add("SMS");
                channels.add("EMAIL");
                channels.add("PUSH");
                channels.add("DASHBOARD");
                break;
            case WARNING:
                channels.add("EMAIL");
                channels.add("PUSH");
                channels.add("DASHBOARD");
                break;
            case INFO:
                channels.add("DASHBOARD");
                break;
        }
        
        // Send notifications through appropriate channels
        for (String channel : channels) {
            sendNotificationThroughChannel(alert, channel);
        }
    }
    
    private void sendNotificationThroughChannel(TrackerAlert alert, String channel) {
        // Implementation for different notification channels
        System.out.println("Sending " + channel + " notification: " + alert.getMessage());
    }
    
    private boolean isRuleInCooldown(AlertRule rule, String vehicleId) {
        String cacheKey = getRuleCooldownKey(rule.getRuleId(), vehicleId);
        Instant lastTriggered = ruleCooldownCache.get(cacheKey);
        
        if (lastTriggered == null) return false;
        
        boolean inCooldown = rule.isInCooldown(lastTriggered);
        
        // Clean up old cache entries
        if (!inCooldown) {
            ruleCooldownCache.remove(cacheKey);
        }
        
        return inCooldown;
    }
    
    private String getRuleCooldownKey(Long ruleId, String vehicleId) {
        return ruleId + "_" + vehicleId;
    }
    
    @Transactional
    public void acknowledgeAlert(Long alertId, String acknowledgedBy) {
        TrackerAlert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        
        alert.acknowledge(acknowledgedBy);
        alertRepository.save(alert);
    }
    
    @Transactional
    public void resolveAlert(Long alertId, String resolvedBy, String resolutionNotes) {
        TrackerAlert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        
        alert.resolve(resolvedBy, resolutionNotes);
        alertRepository.save(alert);
    }
    
    @Transactional(readOnly = true)
    public Page<TrackerAlert> getVehicleAlerts(String vehicleId, Pageable pageable) {
        return alertRepository.findByVehicleVehicleId(vehicleId, pageable);
    }
    
    @Transactional(readOnly = true)
    public List<TrackerAlert> getActiveAlerts(int limit) {
        return alertRepository.findActiveAlerts(
            org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getAlertStatistics(Instant startTime, Instant endTime) {
        Map<String, Object> stats = new HashMap<>();
        
        // Total alerts
        long totalAlerts = alertRepository.count();
        stats.put("totalAlerts", totalAlerts);
        
        // Unacknowledged alerts by severity
        for (AlertSeverity severity : AlertSeverity.values()) {
            long count = alertRepository.countUnacknowledgedBySeverity(severity);
            stats.put("unacknowledged_" + severity.name().toLowerCase(), count);
        }
        
        // Alert type distribution
        List<Object[]> typeStats = alertRepository.getAlertTypeStatistics(startTime);
        Map<String, Long> typeDistribution = typeStats.stream()
            .collect(Collectors.toMap(
                arr -> (String) arr[0],
                arr -> (Long) arr[1]
            ));
        stats.put("typeDistribution", typeDistribution);
        
        return stats;
    }
    
    @Transactional
    public void autoResolveStaleAlerts(int hoursThreshold) {
        Instant cutoffTime = Instant.now().minus(Duration.ofHours(hoursThreshold));
        List<TrackerAlert> staleAlerts = alertRepository.findStaleAlerts(cutoffTime);
        
        for (TrackerAlert alert : staleAlerts) {
            if (!alert.getResolved()) {
                alert.resolve("SYSTEM", "Automatically resolved after " + hoursThreshold + " hours");
                alertRepository.save(alert);
            }
        }
    }
}