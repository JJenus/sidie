package com.jjenus.tracker.core.api.dto;

import lombok.Data;
import org.springframework.data.domain.Sort;

import java.time.Instant;

@Data
public class LocationSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "recordedAt";
    private Sort.Direction sortDirection = Sort.Direction.DESC;
    private String trackerId;
    private String vehicleId;
    private Instant fromTime;
    private Instant toTime;
    private Boolean accStatus;
    private Float minSpeed;
    private Float maxSpeed;
    private Boolean validOnly = true;
}
