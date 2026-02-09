package com.jjenus.tracker.core.api.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Range;

@Data
public class TrackerStatusRequest {
    @Range(min = 0, max = 100, message = "Battery level must be 0-100%")
    private Float batteryLevel;

    @Range(min = 0, max = 5, message = "Signal strength must be 0-5")
    private Integer signalStrength;

    private Boolean isOnline;
}
