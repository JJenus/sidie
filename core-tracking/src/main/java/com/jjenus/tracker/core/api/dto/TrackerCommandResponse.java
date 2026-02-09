package com.jjenus.tracker.core.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.domain.enums.CommandType;
import lombok.Data;

import java.time.Instant;

@Data
public class TrackerCommandResponse {
    private Long commandId;
    private String trackerId;
    private CommandType commandType;
    private String commandData;
    private CommandStatus status;
    private String responseData;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private String initiatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant respondedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;
}
