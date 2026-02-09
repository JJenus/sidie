package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.LocationResponse;
import com.jjenus.tracker.core.api.dto.LocationSearchRequest;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.application.service.LocationCommandService;
import com.jjenus.tracker.core.application.service.LocationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Location tracking APIs")
public class LocationController {

    private final LocationQueryService locationQueryService;
    private final LocationCommandService locationCommandService;

    @GetMapping("/{locationId}")
    @Operation(summary = "Get location by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Location found"),
            @ApiResponse(responseCode = "404", description = "Location not found")
    })
    public ResponseEntity<LocationResponse> getLocation(
            @Parameter(description = "Location ID") @PathVariable Long locationId) {
        LocationResponse response = locationQueryService.getLocation(locationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}")
    @Operation(summary = "Get locations for tracker")
    public ResponseEntity<List<LocationResponse>> getTrackerLocations(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId) {
        List<LocationResponse> response = locationQueryService.getTrackerLocations(trackerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search locations with filters")
    public ResponseEntity<PagedResponse<LocationResponse>> searchLocations(
            @Parameter(description = "Search criteria") @ModelAttribute LocationSearchRequest request) {
        PagedResponse<LocationResponse> response = locationQueryService.searchLocations(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}/latest")
    @Operation(summary = "Get latest location by device ID")
    public ResponseEntity<LocationResponse> getLatestLocationByDeviceId(
            @Parameter(description = "Device ID") @PathVariable String deviceId) {
        LocationResponse response = locationQueryService.getLatestLocationByDeviceId(deviceId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    @GetMapping("/tracker/{trackerId}/count")
    @Operation(summary = "Get location count for tracker")
    public ResponseEntity<Long> getLocationCountByTracker(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId) {
        Long count = locationQueryService.getLocationCountByTracker(trackerId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/acc-off-events")
    @Operation(summary = "Get ACC off events")
    public ResponseEntity<List<LocationResponse>> getAccOffEvents(
            @Parameter(description = "Start time (ISO 8601)") @RequestParam Instant startTime) {
        List<LocationResponse> response = locationQueryService.getAccOffEvents(startTime);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/record")
    @Operation(summary = "Record a new location")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Location recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid location data"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<LocationResponse> recordLocation(
            @RequestParam String trackerId,
            @RequestParam String deviceId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Float speedKmh,
            @RequestParam Instant recordedAt) {

        if (speedKmh == null) {
            speedKmh = 0.0f;
        }

        throw new HttpClientErrorException(HttpStatus.FORBIDDEN,"Action not allowed");

//        LocationResponse response = locationCommandService.recordLocation(
//                trackerId, latitude, longitude, speedKmh, recordedAt);
//        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{locationId}")
    @Operation(summary = "Delete location")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Location deleted"),
            @ApiResponse(responseCode = "404", description = "Location not found")
    })
    public ResponseEntity<Void> deleteLocation(
            @Parameter(description = "Location ID") @PathVariable Long locationId) {
        locationCommandService.deleteLocation(locationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{locationId}/update-field")
    @Operation(summary = "Update location field")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Field updated"),
            @ApiResponse(responseCode = "400", description = "Invalid field"),
            @ApiResponse(responseCode = "404", description = "Location not found")
    })
    public ResponseEntity<LocationResponse> updateLocationField(
            @Parameter(description = "Location ID") @PathVariable Long locationId,
            @RequestParam String field,
            @RequestParam String value) {
        LocationResponse response = locationCommandService.updateLocationData(locationId, field, value);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Cleanup old locations")
    public ResponseEntity<Integer> cleanupOldLocations(
            @Parameter(description = "Cutoff time (ISO 8601)") @RequestParam Instant cutoffTime) {
        int deleted = locationCommandService.cleanupOldLocations(cutoffTime);
        return ResponseEntity.ok(deleted);
    }
}