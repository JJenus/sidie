package com.jjenus.tracker.core.api.controller;

import com.jjenus.tracker.core.api.dto.TrackerCommandRequest;
import com.jjenus.tracker.core.api.dto.TrackerCommandResponse;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.application.service.TrackerComCommandService;
import com.jjenus.tracker.core.application.service.TrackerComQueryService;
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
@Tag(name = "Tracker Commands", description = "Tracker command management APIs")
public class TrackerCommandController {

    private final TrackerComQueryService commandQueryService;
    private final TrackerComCommandService commandCommandService;

    @PostMapping
    @Operation(summary = "Create a new tracker command")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Command created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Tracker not found")
    })
    public ResponseEntity<TrackerCommandResponse> createCommand(@Valid @RequestBody TrackerCommandRequest request) {
        TrackerCommandResponse response = commandCommandService.createCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{commandId}")
    @Operation(summary = "Get command by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command found"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<TrackerCommandResponse> getCommand(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        TrackerCommandResponse response = commandQueryService.getCommand(commandId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}")
    @Operation(summary = "Get commands for tracker")
    public ResponseEntity<PagedResponse<TrackerCommandResponse>> getCommandsByTracker(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<TrackerCommandResponse> response = commandQueryService.getCommandsByTracker(trackerId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get commands by status")
    public ResponseEntity<List<TrackerCommandResponse>> getCommandsByStatus(
            @Parameter(description = "Command status") @PathVariable CommandStatus status) {
        List<TrackerCommandResponse> response = commandQueryService.getCommandsByStatus(status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}/status/{status}")
    @Operation(summary = "Get commands for tracker by status")
    public ResponseEntity<List<TrackerCommandResponse>> getCommandsByTrackerAndStatus(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId,
            @Parameter(description = "Command status") @PathVariable CommandStatus status) {
        List<TrackerCommandResponse> response = commandQueryService.getCommandsByTrackerAndStatus(trackerId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-retryable")
    @Operation(summary = "Get pending and retryable commands")
    public ResponseEntity<List<TrackerCommandResponse>> getPendingAndRetryableCommands(
            @Parameter(description = "Cutoff time (ISO 8601)") @RequestParam Instant cutoffTime) {
        List<TrackerCommandResponse> response = commandQueryService.getPendingAndRetryableCommands(cutoffTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracker/{trackerId}/recent")
    @Operation(summary = "Get recent commands by tracker ID")
    public ResponseEntity<List<TrackerCommandResponse>> getRecentCommandsByTrackerId(
            @Parameter(description = "Tracker ID") @PathVariable String trackerId) {
        List<TrackerCommandResponse> response = commandQueryService.getRecentCommandsByTrackerId(trackerId);
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
    public ResponseEntity<TrackerCommandResponse> markAsSent(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        TrackerCommandResponse response = commandCommandService.markAsSent(commandId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/delivered")
    @Operation(summary = "Mark command as delivered")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command marked as delivered"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<TrackerCommandResponse> markAsDelivered(
            @Parameter(description = "Command ID") @PathVariable Long commandId,
            @RequestParam String responseData) {
        TrackerCommandResponse response = commandCommandService.markAsDelivered(commandId, responseData);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/failed")
    @Operation(summary = "Mark command as failed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command marked as failed"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<TrackerCommandResponse> markAsFailed(
            @Parameter(description = "Command ID") @PathVariable Long commandId,
            @RequestParam String error) {
        TrackerCommandResponse response = commandCommandService.markAsFailed(commandId, error);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commandId}/retry")
    @Operation(summary = "Retry command")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command retry initiated"),
            @ApiResponse(responseCode = "400", description = "Command cannot be retried"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    public ResponseEntity<TrackerCommandResponse> retryCommand(
            @Parameter(description = "Command ID") @PathVariable Long commandId) {
        TrackerCommandResponse response = commandCommandService.retryCommand(commandId);
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