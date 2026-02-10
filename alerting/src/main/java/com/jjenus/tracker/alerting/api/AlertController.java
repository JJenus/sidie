package com.jjenus.tracker.alerting.api;

import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.application.service.AlertService;
import com.jjenus.tracker.alerting.application.service.AlertQueryService;
import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Alert management endpoints")
public class AlertController {

  private final AlertService alertService;
  private final AlertQueryService alertQueryService;
  
  public AlertController(AlertService alertService, AlertQueryService alertQueryService) {
      this.alertService = alertService;
      this.alertQueryService = alertQueryService;
  }

    // ========== CRUD ENDPOINTS ==========

    @PostMapping
    @Operation(summary = "Create a new alert")
    public ResponseEntity<AlertResponse> createAlert(@Valid @RequestBody CreateAlertRequest request) {
        AlertResponse response = alertService.createAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{alertId}")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<AlertResponse> getAlert(
            @Parameter(name = "alertId", description = "Alert ID") 
            @PathVariable Long alertId) {
        AlertResponse response = alertService.getAlertById(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Search alerts with pagination and filtering")
    public ResponseEntity<PagedResponse<AlertResponse>> searchAlerts(
            @Parameter(name = "page", description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(name = "size", description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(name = "sortBy", description = "Sort field")
            @RequestParam(defaultValue = "triggeredAt") String sortBy,

            @Parameter(name = "sortDirection", description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(name = "search", description = "Search in message")
            @RequestParam(required = false) String search,

            @Parameter(name = "vehicleId", description = "Filter by vehicle ID")
            @RequestParam(required = false) String vehicleId,

            @Parameter(name = "trackerId", description = "Filter by tracker ID")
            @RequestParam(required = false) String trackerId,

            @Parameter(name = "alertType", description = "Filter by alert type")
            @RequestParam(required = false) AlertType alertType,

            @Parameter(name = "severity", description = "Filter by severity")
            @RequestParam(required = false) AlertSeverity severity,

            @Parameter(name = "acknowledged", description = "Filter by acknowledged status")
            @RequestParam(required = false) Boolean acknowledged,

            @Parameter(name = "resolved", description = "Filter by resolved status")
            @RequestParam(required = false) Boolean resolved,

            @Parameter(name = "startDate", description = "Start date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,

            @Parameter(name = "endDate", description = "End date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        AlertSearchRequest searchRequest = new AlertSearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setVehicleId(vehicleId);
        searchRequest.setTrackerId(trackerId);
        searchRequest.setAlertType(alertType);
        searchRequest.setSeverity(severity);
        searchRequest.setAcknowledged(acknowledged);
        searchRequest.setResolved(resolved);
        searchRequest.setStartDate(startDate);
        searchRequest.setEndDate(endDate);

        PagedResponse<AlertResponse> response = alertService.searchAlerts(searchRequest);
        return ResponseEntity.ok(response);
    }

    // ========== ALERT MANAGEMENT ==========

    @PostMapping("/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @Parameter(name = "alertId", description = "Alert ID") 
            @PathVariable Long alertId,
            @Valid @RequestBody AcknowledgeAlertRequest request) {
        AlertResponse response = alertService.acknowledgeAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<AlertResponse> resolveAlert(
            @Parameter(name = "alertId", description = "Alert ID") 
            @PathVariable Long alertId,
            @Valid @RequestBody ResolveAlertRequest request) {
        AlertResponse response = alertService.resolveAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bulk/acknowledge")
    @Operation(summary = "Bulk acknowledge alerts")
    public ResponseEntity<Void> bulkAcknowledgeAlerts(
            @RequestBody List<Long> alertIds,
            @Parameter(name = "acknowledgedBy", description = "User who acknowledged the alerts") 
            @RequestParam String acknowledgedBy) {
        alertService.bulkAcknowledgeAlerts(alertIds, acknowledgedBy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk/resolve")
    @Operation(summary = "Bulk resolve alerts")
    public ResponseEntity<Void> bulkResolveAlerts(
            @RequestBody List<Long> alertIds,
            @Parameter(name = "resolvedBy", description = "User who resolved the alerts") 
            @RequestParam String resolvedBy,
            @Parameter(name = "resolutionNotes", description = "Resolution notes") 
            @RequestParam String resolutionNotes) {
        alertService.bulkResolveAlerts(alertIds, resolvedBy, resolutionNotes);
        return ResponseEntity.ok().build();
    }

    // ========== STATUS ENDPOINTS ==========

    @GetMapping("/active")
    @Operation(summary = "Get active alerts with pagination")
    public ResponseEntity<PagedResponse<AlertResponse>> getActiveAlerts(
            @Parameter(name = "page", description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(name = "size", description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        PagedResponse<AlertResponse> response = alertService.getActiveAlertsPaged(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(summary = "Get active alerts for a vehicle")
    public ResponseEntity<List<AlertResponse>> getVehicleActiveAlerts(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId) {
        List<AlertResponse> response = alertService.getActiveAlerts(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/recent")
    @Operation(summary = "Get recent alerts for a vehicle")
    public ResponseEntity<List<AlertResponse>> getVehicleRecentAlerts(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId,
            @Parameter(name = "limit", description = "Maximum number of alerts to return") 
            @RequestParam(defaultValue = "10") int limit) {

        List<AlertResponse> response = alertService.getRecentAlerts(vehicleId, limit);
        return ResponseEntity.ok(response);
    }

    // ========== STATISTICS ENDPOINTS ==========

    @GetMapping("/stats")
    @Operation(summary = "Get alert statistics")
    public ResponseEntity<Map<String, Long>> getAlertStatistics(
            @Parameter(name = "startDate", description = "Start date for statistics (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate) {

        Instant start = startDate != null ? startDate : Instant.now().minusSeconds(7 * 24 * 60 * 60); // Default: last 7 days
        Map<String, Long> stats = alertQueryService.getAlertStatistics(start, Instant.now());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/unacknowledged")
    @Operation(summary = "Get count of unacknowledged alerts by severity")
    public ResponseEntity<Map<String, Long>> getUnacknowledgedCounts() {
        Map<String, Long> counts = alertService.getUnacknowledgedCountBySeverity();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/vehicle/{vehicleId}/has-critical")
    @Operation(summary = "Check if vehicle has critical unacknowledged alerts")
    public ResponseEntity<Boolean> hasCriticalUnacknowledgedAlerts(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId) {
        boolean hasCritical = alertService.hasCriticalUnacknowledgedAlerts(vehicleId);
        return ResponseEntity.ok(hasCritical);
    }

    // ========== MAINTENANCE ENDPOINTS ==========

    @PostMapping("/cleanup")
    @Operation(summary = "Clean up stale alerts (admin only)")
    public ResponseEntity<Void> cleanupStaleAlerts(
            @Parameter(name = "cutoffTime", description = "Cutoff time for stale alerts (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cutoffTime) {
        alertService.cleanupStaleAlerts(cutoffTime);
        return ResponseEntity.ok().build();
    }

    // ========== AUTOMATED ALERT ENDPOINTS ==========

    @PostMapping("/automated")
    @Operation(summary = "Create an automated alert (for internal use)")
    public ResponseEntity<AlertResponse> createAutomatedAlert(
            @Parameter(name = "vehicleId", description = "Vehicle ID")
            @RequestParam String vehicleId,
            
            @Parameter(name = "trackerId", description = "Tracker ID")
            @RequestParam String trackerId,
            
            @Parameter(name = "alertType", description = "Alert type")
            @RequestParam AlertType alertType,
            
            @Parameter(name = "severity", description = "Alert severity")
            @RequestParam AlertSeverity severity,
            
            @Parameter(name = "message", description = "Alert message")
            @RequestParam String message,
            
            @RequestBody(required = false) Map<String, Object> metadata) {

        alertService.processAutomatedAlert(vehicleId, trackerId, alertType, severity, message, metadata);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}