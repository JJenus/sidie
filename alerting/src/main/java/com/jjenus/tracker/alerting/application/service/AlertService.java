package com.jjenus.tracker.alerting.application.service;

import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.domain.entity.TrackerAlert;
import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final TrackerAlertRepository alertRepository;
    private final AlertQueryService alertQueryService;

    public AlertService(TrackerAlertRepository alertRepository, AlertQueryService alertQueryService) {
        this.alertRepository = alertRepository;
        this.alertQueryService = alertQueryService;
    }

    // ========== CRUD OPERATIONS ==========

    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "alerts", key = "'active_' + #request.vehicleId"),
                    @CacheEvict(value = "alerts", key = "'recent_' + #request.vehicleId"),
                    @CacheEvict(value = "alertStats", allEntries = true)
            }
    )
    public AlertResponse createAlert(CreateAlertRequest request) {
        logger.info("Creating alert for vehicle: {}, type: {}",
                request.getVehicleId(), request.getAlertType());

        TrackerAlert alert = new TrackerAlert();
        alert.setTracker(request.getTrackerId());
        alert.setVehicle(request.getVehicleId());
        alert.setAlertType(request.getAlertType());
        alert.setSeverity(request.getSeverity());
        alert.setMessage(request.getMessage());

        if (request.getLatitude() != null && request.getLongitude() != null) {
            alert.setLocation(String.format("%s,%s",
                    request.getLatitude(), request.getLongitude()));
        }

        if (request.getSpeedKmh() != null) {
            alert.addMetadata("speedKmh", request.getSpeedKmh());
        }

        if (request.getMetadata() != null) {
            request.getMetadata().forEach(alert::addMetadata);
        }

        alert.setTriggeredAt(Instant.now());
        alert.setAcknowledged(false);
        alert.setResolved(false);

        TrackerAlert saved = alertRepository.save(alert);
        logger.info("Alert created with ID: {}", saved.getAlertId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alerts", key = "#alertId", unless = "#result == null")
    public AlertResponse getAlertById(Long alertId) {
        TrackerAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> AlertException.alertNotFound(alertId));
        return toResponse(alert);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alertsPaged", key = "'search_' + #searchRequest.hashCode()")
    public PagedResponse<AlertResponse> searchAlerts(AlertSearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<TrackerAlert> page = alertRepository.searchAlerts(
                searchRequest.getVehicleId(),
                searchRequest.getTrackerId(),
                searchRequest.getAlertType() != null ? searchRequest.getAlertType().name() : null,
                searchRequest.getSeverity(),
                searchRequest.getAcknowledged(),
                searchRequest.getResolved(),
                searchRequest.getStartDate(),
                searchRequest.getEndDate(),
                searchRequest.getSearch(),
                pageable);

        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alerts", key = "'active_' + #vehicleId")
    public List<AlertResponse> getActiveAlerts(String vehicleId) {
        List<TrackerAlert> alerts = alertRepository.findActiveVehicleAlerts(vehicleId);
        return alerts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alertsPaged", key = "'activePaged_' + #page + '_' + #size")
    public PagedResponse<AlertResponse> getActiveAlertsPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "severity", "triggeredAt"));
        Page<TrackerAlert> pageResult = alertRepository.findActiveAlerts(pageable);
        return new PagedResponse<>(pageResult.map(this::toResponse));
    }

    // ========== ALERT MANAGEMENT ==========

    @Transactional
    @Caching(
            put = @CachePut(value = "alerts", key = "#alertId"),
            evict = {
                    @CacheEvict(value = "alerts", key = "'active_' + #result.vehicleId"),
                    @CacheEvict(value = "alertStats", allEntries = true)
            }
    )
    public AlertResponse acknowledgeAlert(Long alertId, AcknowledgeAlertRequest request) {
        logger.info("Acknowledging alert: {} by {}", alertId, request.getAcknowledgedBy());

        TrackerAlert alert = alertQueryService.getAlertById(alertId);

        if (Boolean.TRUE.equals(alert.getAcknowledged())) {
            logger.warn("Alert {} already acknowledged", alertId);
            return toResponse(alert);
        }

        alert.acknowledge(request.getAcknowledgedBy());
        TrackerAlert updated = alertRepository.save(alert);

        logger.info("Alert {} acknowledged successfully", alertId);
        return toResponse(updated);
    }

    @Transactional
    @Caching(
            put = @CachePut(value = "alerts", key = "#alertId"),
            evict = {
                    @CacheEvict(value = "alerts", key = "'active_' + #result.vehicleId"),
                    @CacheEvict(value = "alertStats", allEntries = true)
            }
    )
    public AlertResponse resolveAlert(Long alertId, ResolveAlertRequest request) {
        logger.info("Resolving alert: {} by {}", alertId, request.getResolvedBy());

        TrackerAlert alert = alertQueryService.getAlertById(alertId);

        if (Boolean.TRUE.equals(alert.getResolved())) {
            logger.warn("Alert {} already resolved", alertId);
            return toResponse(alert);
        }

        alert.resolve(request.getResolvedBy(), request.getResolutionNotes());
        TrackerAlert updated = alertRepository.save(alert);

        logger.info("Alert {} resolved successfully", alertId);
        return toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = {"alerts", "alertsPaged", "alertStats"}, allEntries = true)
    public void bulkAcknowledgeAlerts(List<Long> alertIds, String acknowledgedBy) {
        logger.info("Bulk acknowledging {} alerts by {}", alertIds.size(), acknowledgedBy);

        List<TrackerAlert> alerts = alertRepository.findAllById(alertIds);

        for (TrackerAlert alert : alerts) {
            if (!Boolean.TRUE.equals(alert.getAcknowledged())) {
                alert.acknowledge(acknowledgedBy);
            }
        }

        alertRepository.saveAll(alerts);
        logger.info("Bulk acknowledged {} alerts", alerts.size());
    }

    @Transactional
    @CacheEvict(value = {"alerts", "alertsPaged", "alertStats"}, allEntries = true)
    public void bulkResolveAlerts(List<Long> alertIds, String resolvedBy, String resolutionNotes) {
        logger.info("Bulk resolving {} alerts by {}", alertIds.size(), resolvedBy);

        List<TrackerAlert> alerts = alertRepository.findAllById(alertIds);

        for (TrackerAlert alert : alerts) {
            if (!Boolean.TRUE.equals(alert.getResolved())) {
                alert.resolve(resolvedBy, resolutionNotes);
            }
        }

        alertRepository.saveAll(alerts);
        logger.info("Bulk resolved {} alerts", alerts.size());
    }

    @Transactional
    @CacheEvict(value = {"alerts", "alertsPaged", "alertStats"}, allEntries = true)
    public void cleanupStaleAlerts(Instant cutoffTime) {
        logger.info("Cleaning up alerts older than {}", cutoffTime);

        List<TrackerAlert> staleAlerts = alertRepository.findStaleAlerts(cutoffTime);
        int staleCount = staleAlerts.size();

        for (TrackerAlert alert : staleAlerts) {
            if (!Boolean.TRUE.equals(alert.getResolved())) {
                alert.resolve("system", "Auto-resolved due to staleness");
            }
        }

        alertRepository.saveAll(staleAlerts);
        logger.info("Auto-resolved {} stale alerts", staleCount);
    }

    // ========== HELPER METHODS ==========

    private Pageable createPageable(AlertSearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    private AlertResponse toResponse(TrackerAlert alert) {
        AlertResponse response = new AlertResponse();
        response.setAlertId(alert.getAlertId());
        response.setTrackerId(alert.getTracker());
        response.setVehicleId(alert.getVehicle());
        response.setAlertType(alert.getAlertType());
        response.setSeverity(alert.getSeverity());
        response.setMessage(alert.getMessage());

        // Extract speed from metadata
        if (alert.getMetadata() != null && alert.getMetadata().containsKey("speedKmh")) {
            response.setSpeedKmh(((Number) alert.getMetadata().get("speedKmh")).floatValue());
        }

        response.setTriggeredAt(alert.getTriggeredAt());
        response.setAcknowledged(alert.getAcknowledged());
        response.setAcknowledgedBy(alert.getAcknowledgedBy());
        response.setAcknowledgedAt(alert.getAcknowledgedAt());
        response.setResolved(alert.getResolved());
        response.setResolvedBy(alert.getResolvedBy());
        response.setResolvedAt(alert.getResolvedAt());
        response.setResolutionNotes(alert.getResolutionNotes());
        response.setMetadata(alert.getMetadata());
        response.setCreatedAt(alert.getCreatedAt());
        response.setUpdatedAt(alert.getUpdatedAt());

        return response;
    }

    // ========== BUSINESS METHODS ==========

    @Transactional(readOnly = true)
    @Cacheable(value = "alertStats", key = "'hasCritical_' + #vehicleId")
    public boolean hasCriticalUnacknowledgedAlerts(String vehicleId) {
        List<TrackerAlert> criticalAlerts = alertRepository.findBySeverityAndAcknowledged(
                AlertSeverity.CRITICAL, false);

        return criticalAlerts.stream()
                .anyMatch(alert -> vehicleId.equals(alert.getVehicle()) &&
                        Boolean.FALSE.equals(alert.getAcknowledged()));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alertStats", key = "'unacknowledgedCounts'")
    public Map<String, Long> getUnacknowledgedCountBySeverity() {
        Map<String, Long> counts = Map.of(
                "INFO", alertRepository.countUnacknowledgedBySeverity(AlertSeverity.INFO),
                "WARNING", alertRepository.countUnacknowledgedBySeverity(AlertSeverity.WARNING),
                "CRITICAL", alertRepository.countUnacknowledgedBySeverity(AlertSeverity.CRITICAL)
        );
        return counts;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "alerts", key = "'recent_' + #vehicleId + '_' + #limit")
    public List<AlertResponse> getRecentAlerts(String vehicleId, int limit) {
        Instant startTime = Instant.now().minusSeconds(24 * 60 * 60);
        List<TrackerAlert> alerts = alertRepository.findVehicleAlertsInRange(
                vehicleId, startTime, Instant.now());

        return alerts.stream()
                .limit(limit)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"alerts", "alertsPaged", "alertStats"}, allEntries = true)
    public void processAutomatedAlert(String vehicleId, String trackerId,
                                      AlertType alertType, AlertSeverity severity,
                                      String message, Map<String, Object> metadata) {

        CreateAlertRequest request = new CreateAlertRequest();
        request.setVehicleId(vehicleId);
        request.setTrackerId(trackerId);
        request.setAlertType(alertType);
        request.setSeverity(severity);
        request.setMessage(message);
        request.setMetadata(metadata);

        createAlert(request);

        // Notify relevant systems about critical alerts
        if (severity == AlertSeverity.CRITICAL) {
            notifyCriticalAlert(vehicleId, alertType, message);
        }
    }

    private void notifyCriticalAlert(String vehicleId, AlertType alertType, String message) {
        logger.warn("CRITICAL ALERT - Vehicle: {}, Type: {}, Message: {}",
                vehicleId, alertType, message);
    }
}