package com.jjenus.tracker.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrackerRequest {
    @NotBlank(message = "Tracker ID is required")
    @Size(min = 3, max = 50, message = "Tracker ID must be 3-50 characters")
    private String trackerId;

    @NotBlank(message = "Device ID is required")
    @Size(max = 50, message = "Device ID must be max 50 characters")
    private String deviceId;

    @Size(max = 100, message = "Model must be max 100 characters")
    private String model;

    @Size(max = 20, message = "Protocol must be max 20 characters")
    private String protocol;

    @Size(max = 50, message = "Firmware version must be max 50 characters")
    private String firmwareVersion;

    @Size(max = 20, message = "SIM number must be max 20 characters")
    private String simNumber;

    private String vehicleId;
}