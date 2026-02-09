package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.application.service.VehicleCommandService;
import com.jjenus.tracker.core.application.service.VehicleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Vehicle management APIs")
public class VehicleController {

    private final VehicleQueryService vehicleQueryService;
    private final VehicleCommandService vehicleCommandService;

    @PostMapping
    @Operation(summary = "Create a new vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vehicle created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Vehicle already exists")
    })
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleCommandService.createVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Get vehicle by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle found"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    public ResponseEntity<VehicleResponse> getVehicle(
            @Parameter(description = "Vehicle ID") @PathVariable String vehicleId) {
        VehicleResponse response = vehicleQueryService.getVehicle(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get vehicle by device ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle found"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    public ResponseEntity<VehicleResponse> getVehicleByDeviceId(
            @Parameter(description = "Device ID") @PathVariable String deviceId) {
        VehicleResponse response = vehicleQueryService.getVehicleByDeviceId(deviceId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Update vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @Parameter(description = "Vehicle ID") @PathVariable String vehicleId,
            @Valid @RequestBody VehicleUpdateRequest request) {
        VehicleResponse response = vehicleCommandService.updateVehicle(vehicleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Delete vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Vehicle deleted"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    public ResponseEntity<Void> deleteVehicle(
            @Parameter(description = "Vehicle ID") @PathVariable String vehicleId) {
        vehicleCommandService.deleteVehicle(vehicleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get list of vehicles (paginated)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of vehicles returned"),
    })
    public ResponseEntity<PagedResponse<VehicleResponse>> getVehicles(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "vehicleId") String sortBy,
            @Parameter(description = "Sort direction (ASC/DESC)") @RequestParam(defaultValue = "ASC") String sortDirection) {

        VehicleSearchRequest searchRequest = new VehicleSearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(Sort.Direction.fromString(sortDirection));

        // No filters applied → full list
        PagedResponse<VehicleResponse> response = vehicleQueryService.searchVehicles(searchRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search vehicles with filters")
    public ResponseEntity<PagedResponse<VehicleResponse>> searchVehicles(
            @Parameter(description = "Search criteria") @ModelAttribute VehicleSearchRequest request) {
        PagedResponse<VehicleResponse> response = vehicleQueryService.searchVehicles(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active-trips")
    @Operation(summary = "Get vehicles with active trips")
    public ResponseEntity<List<VehicleResponse>> getVehiclesWithActiveTrips() {
        List<VehicleResponse> response = vehicleQueryService.getVehiclesWithActiveTrips();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fuel-cut-active")
    @Operation(summary = "Get vehicles with active fuel cut")
    public ResponseEntity<List<VehicleResponse>> getVehiclesWithActiveFuelCut() {
        List<VehicleResponse> response = vehicleQueryService.getVehiclesWithActiveFuelCut();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stale-telemetry")
    @Operation(summary = "Get vehicles with stale telemetry")
    public ResponseEntity<List<VehicleResponse>> getVehiclesWithStaleTelemetry(
            @Parameter(description = "Cutoff time (ISO 8601)")
            @RequestParam Instant cutoffTime) {
        List<VehicleResponse> response = vehicleQueryService.getVehiclesWithStaleTelemetry(cutoffTime);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{vehicleId}/status")
    @Operation(summary = "Update vehicle status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    public ResponseEntity<Void> updateVehicleStatus(
            @Parameter(description = "Vehicle ID") @PathVariable String vehicleId,
            @RequestParam String deviceId,
            @RequestParam boolean online) {
        vehicleCommandService.updateVehicleStatus(vehicleId, deviceId, online);
        return ResponseEntity.ok().build();
    }
}