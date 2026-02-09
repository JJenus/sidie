package com.jjenus.tracker.core.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

@Data
public class TrackerUpdateRequest {
    @Size(max = 100, message = "Model must be max 100 characters")
    private String model;

    @Size(max = 20, message = "Protocol must be max 20 characters")
    private String protocol;

    @Size(max = 50, message = "Firmware version must be max 50 characters")
    private String firmwareVersion;

    @Size(max = 20, message = "SIM number must be max 20 characters")
    private String simNumber;

    @Range(min = 0, max = 100, message = "Battery level must be 0-100%")
    private Float batteryLevel;

    @Range(min = 0, max = 5, message = "Signal strength must be 0-5")
    private Integer signalStrength;

    private Boolean isOnline;
    private String vehicleId;
}
