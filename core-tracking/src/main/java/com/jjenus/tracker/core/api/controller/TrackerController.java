package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.application.service.TrackerCommandService;
import com.jjenus.tracker.core.application.service.TrackerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trackers")
@RequiredArgsConstructor
@Tag(name = "Trackers", description = "Tracker device management APIs")
public class TrackerController {

    private final TrackerQueryService trackerQueryService;
    private final TrackerCommandService trackerCommandService;

    @PostMapping
    @Operation(summary = "Create a new tracker")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tracker created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Tracker already exists")
    })
    public ResponseEntity<TrackerResponse> createTracker(@Valid @RequestBody TrackerRequest request) {
        TrackerResponse response = trackerCommandService.createTracker(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get paginated list of trackers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paginated list of trackers returned")
    })
    public ResponseEntity<PagedResponse<TrackerResponse>> getTrackers(
            @Parameter(name = "page", description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(name = "size", description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(name = "sortBy", description = "Sort field (e.g. trackerId, lastSeen, batteryLevel)")
            @RequestParam(defaultValue = "trackerId") String sortBy,

            @Parameter(name = "sortDirection", description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        PagedResponse<TrackerResponse> response =
                trackerQueryService.getTrackersList(page, size, sortBy, sortDirection);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{trackerId}")
    @Operation(summary = "Get tracker by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tracker found"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<TrackerResponse> getTracker(
            @Parameter(name = "trackerId", description = "Tracker ID") 
            @PathVariable String trackerId) {
        TrackerResponse response = trackerQueryService.getTracker(trackerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get tracker by tracker ID (imei)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tracker found"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<TrackerResponse> getTrackerByDeviceId(
            @Parameter(name = "deviceId", description = "Device ID") 
            @PathVariable String deviceId) {
        TrackerResponse response = trackerQueryService.getTrackerByTrackerId(deviceId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{trackerId}")
    @Operation(summary = "Update tracker")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tracker updated"),
            @ApiResponse(responseCode = "404", description = "Tracker not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<TrackerResponse> updateTracker(
            @Parameter(name = "trackerId", description = "Tracker ID") 
            @PathVariable String trackerId,
            @Valid @RequestBody TrackerUpdateRequest request) {
        TrackerResponse response = trackerCommandService.updateTracker(trackerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{trackerId}")
    @Operation(summary = "Delete tracker")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tracker deleted"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<Void> deleteTracker(
            @Parameter(name = "trackerId", description = "Tracker ID") 
            @PathVariable String trackerId) {
        trackerCommandService.deleteTracker(trackerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/online")
    @Operation(summary = "Get all online trackers")
    public ResponseEntity<List<TrackerResponse>> getOnlineTrackers() {
        List<TrackerResponse> response = trackerQueryService.getOnlineTrackers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stale-connections")
    @Operation(summary = "Get stale trackers")
    public ResponseEntity<List<TrackerResponse>> getStaleConnections(
            @Parameter(name = "cutoffTime", description = "Cutoff time (ISO 8601)")
            @RequestParam Instant cutoffTime) {
        List<TrackerResponse> response = trackerQueryService.getStaleConnections(cutoffTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/low-battery")
    @Operation(summary = "Get trackers with low battery")
    public ResponseEntity<List<TrackerResponse>> getTrackersWithLowBattery(
            @Parameter(name = "threshold", description = "Battery threshold percentage")
            @RequestParam(defaultValue = "20") float threshold) {
        List<TrackerResponse> response = trackerQueryService.getTrackersWithLowBattery(threshold);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{trackerId}/status")
    @Operation(summary = "Update tracker status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<TrackerResponse> updateTrackerStatus(
            @Parameter(name = "trackerId", description = "Tracker ID") 
            @PathVariable String trackerId,
            @Valid @RequestBody TrackerStatusRequest request) {
        TrackerResponse response = trackerCommandService.updateTrackerStatus(trackerId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mark-stale-offline")
    @Operation(summary = "Mark stale trackers as offline")
    public ResponseEntity<Void> markStaleTrackersOffline(
            @Parameter(name = "cutoffTime", description = "Cutoff time (ISO 8601)")
            @RequestParam Instant cutoffTime) {
        trackerCommandService.markStaleTrackersOffline(cutoffTime);
        return ResponseEntity.ok().build();
    }
}