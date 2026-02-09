// === LocationResponse.java ===
package com.jjenus.tracker.core.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;

@Data
public class LocationResponse {
    private Double latitude;
    private Double longitude;
    private Float speedKmh;
    private Float heading;
    private Float altitude;
    private String validity;
    private Float odometerKm;
    private Float batteryVoltage;
    private Integer signalStrength;
    private Boolean accStatus;
    private String engineStatus;
    private String deviceStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant recordedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;
}

