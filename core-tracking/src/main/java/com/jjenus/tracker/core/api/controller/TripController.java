package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.application.service.TripCommandService;
import com.jjenus.tracker.core.application.service.TripQueryService;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Trip management and analytics APIs")
public class TripController {

    private final TripQueryService tripQueryService;
    private final TripCommandService tripCommandService;

    @GetMapping("/{tripId}")
    @Operation(summary = "Get trip by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trip found"),
            @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> getTrip(
            @Parameter(name = "tripId", description = "Trip ID") 
            @PathVariable String tripId) {
        TripResponse response = tripQueryService.getTrip(tripId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(summary = "Get active trip for vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active trip found"),
            @ApiResponse(responseCode = "404", description = "No active trip found")
    })
    public ResponseEntity<TripResponse> getActiveTrip(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId) {
        TripResponse response = tripQueryService.getActiveTrip(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Get all trips for vehicle")
    public ResponseEntity<List<TripResponse>> getVehicleTrips(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId) {
        List<TripResponse> response = tripQueryService.getVehicleTrips(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search trips with filters")
    public ResponseEntity<PagedResponse<TripResponse>> searchTrips(
            @Parameter(description = "Search criteria") 
            @ModelAttribute TripSearchRequest request) {
        PagedResponse<TripResponse> response = tripQueryService.searchTrips(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active trips")
    public ResponseEntity<List<TripResponse>> getActiveTrips() {
        List<TripResponse> response = tripQueryService.getActiveTrips();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/statistics")
    @Operation(summary = "Get trip statistics for vehicle")
    public ResponseEntity<TripStatisticsResponse> getTripStatistics(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId,
            
            @Parameter(name = "startTime", description = "Start time (ISO 8601)") 
            @RequestParam Instant startTime,
            
            @Parameter(name = "endTime", description = "End time (ISO 8601)") 
            @RequestParam Instant endTime) {
        TripStatisticsResponse response = tripQueryService.getTripStatistics(vehicleId, startTime, endTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/total-distance")
    @Operation(summary = "Get total distance for vehicle in period")
    public ResponseEntity<Float> getTotalDistanceForPeriod(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId,
            
            @Parameter(name = "startTime", description = "Start time (ISO 8601)") 
            @RequestParam Instant startTime,
            
            @Parameter(name = "endTime", description = "End time (ISO 8601)") 
            @RequestParam Instant endTime) {
        Float distance = tripQueryService.getTotalDistanceForPeriod(vehicleId, startTime, endTime);
        return ResponseEntity.ok(distance);
    }

    @PostMapping("/vehicle/{vehicleId}/end-active")
    @Operation(summary = "End active trip for vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trip ended"),
            @ApiResponse(responseCode = "404", description = "No active trip found")
    })
    public ResponseEntity<Void> endActiveTrip(
            @Parameter(name = "vehicleId", description = "Vehicle ID") 
            @PathVariable String vehicleId,
            
            @Parameter(name = "reason", description = "End reason") 
            @RequestParam TripEndReason reason) {
        tripCommandService.endActiveTripForVehicle(vehicleId, reason);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tripId}/distance")
    @Operation(summary = "Update trip distance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Distance updated"),
            @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<Void> updateTripDistance(
            @Parameter(name = "tripId", description = "Trip ID") 
            @PathVariable String tripId,
            
            @Parameter(name = "distanceKm", description = "Distance in kilometers") 
            @RequestParam float distanceKm) {
        tripCommandService.updateTripDistance(tripId, distanceKm);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tripId}/fuel")
    @Operation(summary = "Update trip fuel consumption")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fuel consumption updated"),
            @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<Void> updateTripFuelConsumption(
            @Parameter(name = "tripId", description = "Trip ID") 
            @PathVariable String tripId,
            
            @Parameter(name = "fuelLiters", description = "Fuel consumption in liters") 
            @RequestParam float fuelLiters) {
        tripCommandService.updateTripFuelConsumption(tripId, fuelLiters);
        return ResponseEntity.ok().build();
    }
}