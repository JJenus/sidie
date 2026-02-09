package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.DeviceCommandRequest;
import com.jjenus.tracker.core.api.dto.DeviceCommandResponse;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.application.service.DeviceCommandCommandService;
import com.jjenus.tracker.core.application.service.DeviceCommandQueryService;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/commands")
@RequiredArgsConstructor
@Tag(name = "Device Commands", description = "Device command management APIs")
public class DeviceCommandController {

    private final DeviceCommandQueryService commandQueryService;
    private final DeviceCommandCommandService commandCommandService;

    @PostMapping
    @Operation(summary = "Create a new device command")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Command created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<DeviceCommandResponse> createCommand(@Valid @RequestBody DeviceCommandRequest request) {
        DeviceCommandResponse response = commandCommandService.createCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{commandId}")
    @Operation(summary = "Get command by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command found"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<DeviceCommandResponse> getCommand(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        DeviceCommandResponse response = commandQueryService.getCommand(commandId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}")
    @Operation(summary = "Get commands for tracker")
    public ResponseEntity<PagedResponse<DeviceCommandResponse>> getCommandsByTracker(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<DeviceCommandResponse> response = commandQueryService.getCommandsByTracker(trackerId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get commands by status")
    public ResponseEntity<List<DeviceCommandResponse>> getCommandsByStatus(
            @Parameter(description = "Command status") @PathVariable CommandStatus status) {
        List<DeviceCommandResponse> response = commandQueryService.getCommandsByStatus(status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}/status/{status}")
    @Operation(summary = "Get commands for tracker by status")
    public ResponseEntity<List<DeviceCommandResponse>> getCommandsByTrackerAndStatus(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId,
            @Parameter(description = "Command status") @PathVariable CommandStatus status) {
        List<DeviceCommandResponse> response = commandQueryService.getCommandsByTrackerAndStatus(trackerId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-retryable")
    @Operation(summary = "Get pending and retryable commands")
    public ResponseEntity<List<DeviceCommandResponse>> getPendingAndRetryableCommands(
            @Parameter(description = "Cutoff time (ISO 8601)") @RequestParam Instant cutoffTime) {
        List<DeviceCommandResponse> response = commandQueryService.getPendingAndRetryableCommands(cutoffTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}/recent")
    @Operation(summary = "Get recent commands by device ID")
    public ResponseEntity<List<DeviceCommandResponse>> getRecentCommandsByDeviceId(
            @Parameter(description = "Device ID") @PathVariable String deviceId) {
        List<DeviceCommandResponse> response = commandQueryService.getRecentCommandsByDeviceId(deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}/pending-count")
    @Operation(summary = "Get pending command count")
    public ResponseEntity<Long> getPendingCommandCount(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId) {
        Long count = commandQueryService.getPendingCommandCount(trackerId);
        return ResponseEntity.ok(count);
    }

    @PutMapping("/{commandId}/sent")
    @Operation(summary = "Mark command as sent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command marked as sent"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<DeviceCommandResponse> markAsSent(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        DeviceCommandResponse response = commandCommandService.markAsSent(commandId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/delivered")
    @Operation(summary = "Mark command as delivered")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command marked as delivered"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<DeviceCommandResponse> markAsDelivered(
            @Parameter(description = "Command ID") @PathVariable Long commandId,
            @RequestParam String responseData) {
        DeviceCommandResponse response = commandCommandService.markAsDelivered(commandId, responseData);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/failed")
    @Operation(summary = "Mark command as failed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command marked as failed"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<DeviceCommandResponse> markAsFailed(
            @Parameter(description = "Command ID") @PathVariable Long commandId,
            @RequestParam String error) {
        DeviceCommandResponse response = commandCommandService.markAsFailed(commandId, error);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/retry")
    @Operation(summary = "Retry command")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command retry initiated"),
            @ApiResponse(responseCode = "400", description = "Command cannot be retried"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<DeviceCommandResponse> retryCommand(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        DeviceCommandResponse response = commandCommandService.retryCommand(commandId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{commandId}")
    @Operation(summary = "Cancel command")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Command cancelled"),
            @ApiResponse(responseCode = "400", description = "Cannot cancel command"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<Void> cancelCommand(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        commandCommandService.cancelCommand(commandId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process-retryable")
    @Operation(summary = "Process retryable commands")
    public ResponseEntity<Void> processRetryableCommands(
            @Parameter(description = "Cutoff time (ISO 8601)") @RequestParam Instant cutoffTime) {
        commandCommandService.processRetryableCommands(cutoffTime);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Cleanup old commands")
    public ResponseEntity<Integer> cleanupOldCommands(
            @Parameter(description = "Cutoff time (ISO 8601)") @RequestParam Instant cutoffTime) {
        int deleted = commandCommandService.cleanupOldCommands(cutoffTime);
        return ResponseEntity.ok(deleted);
    }
}