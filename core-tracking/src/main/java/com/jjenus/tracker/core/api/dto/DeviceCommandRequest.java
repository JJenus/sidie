package com.jjenus.tracker.core.api.dto;

import com.jjenus.tracker.core.domain.enums.CommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceCommandRequest {
    @NotBlank(message = "Tracker ID is required")
    private String trackerId;

    @NotNull(message = "Command type is required")
    private CommandType commandType;

    @NotBlank(message = "Command data is required")
    private String commandData;

    private String initiatedBy;
    private Integer maxRetries = 3;
}
