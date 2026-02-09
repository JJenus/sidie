package com.jjenus.tracker.core.api.dto;

import lombok.Data;

import java.time.Duration;
import java.time.Instant;

@Data
public class TripStatisticsResponse {
    private String vehicleId;
    private Instant periodStart;
    private Instant periodEnd;
    private Integer totalTrips;
    private Float totalDistanceKm;
    private Float averageDistanceKm;
    private Float averageSpeedKmh;
    private Float totalFuelConsumedLiters;
    private Float averageFuelConsumption;
    private Integer totalIdleTimeMinutes;
    private Duration totalDuration;
}
