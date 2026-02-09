package com.jjenus.tracker.core.api.dto;

import com.jjenus.tracker.core.domain.enums.TripEndReason;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.time.Instant;

@Data
public class TripSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "startTime";
    private Sort.Direction sortDirection = Sort.Direction.DESC;
    private String vehicleId;
    private Boolean isActive;
    private TripEndReason endReason;
    private Instant startTimeFrom;
    private Instant startTimeTo;
    private Instant endTimeFrom;
    private Instant endTimeTo;
    private Float minDistance;
    private Float maxDistance;
}
