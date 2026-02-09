package com.jjenus.tracker.core.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjenus.tracker.core.domain.enums.TrackerStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class TrackerResponse {
    private String trackerId;
    private String deviceId;
    private String model;
    private String protocol;
    private String firmwareVersion;
    private String simNumber;
    private Float batteryLevel;
    private Integer signalStrength;
    private Boolean isOnline;
    private TrackerStatus status;
    private String vehicleId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastSeen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;
}
