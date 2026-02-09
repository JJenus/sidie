package com.jjenus.tracker.core.api.dto;

import lombok.Data;
import org.springframework.data.domain.Sort;

import java.time.Instant;

@Data
public class VehicleSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "vehicleId";
    private Sort.Direction sortDirection = Sort.Direction.ASC;
    private String search;
    private String engineState;
    private Boolean accStatus;
    private Boolean fuelCutActive;
    private Boolean hasActiveTrip;
    private Instant lastTelemetryBefore;
    private Instant lastTelemetryAfter;
}
