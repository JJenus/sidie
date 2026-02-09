// === TripResponse.java ===
package com.jjenus.tracker.core.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
import com.jjenus.tracker.core.domain.enums.TripStartReason;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;

@Data
public class TripResponse {
    private String tripId;
    private String vehicleId;
    private LocationResponse startLocation;
    private LocationResponse endLocation;
    private TripStartReason startReason;
    private TripEndReason endReason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant endTime;

    private Integer accOffDurationSeconds;
    private Float totalDistanceKm;
    private Float averageSpeedKmh;
    private Float maxSpeedKmh;
    private Integer idleTimeMinutes;
    private Float fuelConsumedLiters;
    private Boolean isActive;
    private Duration duration;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;
}

