package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.DeviceCommandRequest;
import com.jjenus.tracker.core.api.dto.DeviceCommandResponse;
import com.jjenus.tracker.core.domain.entity.DeviceCommand;
import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.infrastructure.repository.DeviceCommandRepository;
import com.jjenus.tracker.core.infrastructure.repository.TrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeviceCommandCommandService {

    private final DeviceCommandRepository commandRepository;
    private final TrackerRepository trackerRepository;
    private final DeviceCommandQueryService commandQueryService;
    private final ModelMapper modelMapper;

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "'tracker_' + #request.trackerId + '_*'"),
            @CacheEvict(value = "commands", key = "'status_PENDING'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_' + #request.trackerId + '_PENDING'"),
            @CacheEvict(value = "commandStats", key = "'pendingCount_' + #request.trackerId")
    })
    public DeviceCommandResponse createCommand(DeviceCommandRequest request) {
        log.info("Creating command for tracker: {}", request.getTrackerId());

        Tracker tracker = trackerRepository.findById(request.getTrackerId())
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + request.getTrackerId()));

        DeviceCommand command = modelMapper.map(request, DeviceCommand.class);
        command.setTracker(tracker);
        command.setStatus(CommandStatus.PENDING);

        DeviceCommand saved = commandRepository.save(command);

        log.info("Command created: {} for tracker: {}", saved.getCommandId(), tracker.getTrackerId());
        return commandQueryService.getCommand(saved.getCommandId());
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "#commandId"),
            @CacheEvict(value = "commands", key = "'tracker_*'"),
            @CacheEvict(value = "commands", key = "'status_*'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_*'"),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public DeviceCommandResponse markAsSent(Long commandId) {
        log.info("Marking command as sent: {}", commandId);

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsSent();
        DeviceCommand saved = commandRepository.save(command);

        log.info("Command marked as sent: {}", commandId);
        return commandQueryService.getCommand(saved.getCommandId());
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "#commandId"),
            @CacheEvict(value = "commands", key = "'tracker_*'"),
            @CacheEvict(value = "commands", key = "'status_*'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_*'"),
            @CacheEvict(value = "commands", key = "'recentDevice_*'"),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public DeviceCommandResponse markAsDelivered(Long commandId, String response) {
        log.info("Marking command as delivered: {}", commandId);

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsDelivered(response);
        DeviceCommand saved = commandRepository.save(command);

        log.info("Command marked as delivered: {}", commandId);
        return commandQueryService.getCommand(saved.getCommandId());
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "#commandId"),
            @CacheEvict(value = "commands", key = "'tracker_*'"),
            @CacheEvict(value = "commands", key = "'status_*'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_*'"),
            @CacheEvict(value = "commands", key = "'pendingRetryable_*'"),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public DeviceCommandResponse markAsFailed(Long commandId, String error) {
        log.info("Marking command as failed: {}", commandId);

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsFailed(error);
        DeviceCommand saved = commandRepository.save(command);

        log.info("Command marked as failed: {}", commandId);
        return commandQueryService.getCommand(saved.getCommandId());
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "#commandId"),
            @CacheEvict(value = "commands", key = "'tracker_*'"),
            @CacheEvict(value = "commands", key = "'status_*'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_*'"),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public DeviceCommandResponse retryCommand(Long commandId) {
        log.info("Retrying command: {}", commandId);

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        if (!command.canRetry()) {
            throw new IllegalStateException("Command cannot be retried");
        }

        command.incrementRetryCount();
        command.setStatus(CommandStatus.PENDING);
        DeviceCommand saved = commandRepository.save(command);

        log.info("Command retry initiated: {}", commandId);
        return commandQueryService.getCommand(saved.getCommandId());
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "#commandId"),
            @CacheEvict(value = "commands", key = "'tracker_*'"),
            @CacheEvict(value = "commands", key = "'status_*'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_*'"),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public void cancelCommand(Long commandId) {
        log.info("Cancelling command: {}", commandId);

        int updated = commandRepository.cancelPendingCommand(commandId, Instant.now());
        if (updated == 0) {
            throw new IllegalArgumentException("Cannot cancel non-pending command: " + commandId);
        }

        log.info("Command cancelled: {}", commandId);
    }

    @Caching(evict = {
            @CacheEvict(value = "commands", allEntries = true),
            @CacheEvict(value = "commandStats", allEntries = true)
    })
    public int cleanupOldCommands(Instant cutoffTime) {
        log.info("Cleaning up old commands before: {}", cutoffTime);

        int deleted = commandRepository.cleanupOldCommands(cutoffTime);
        log.info("Cleaned up {} old commands", deleted);
        return deleted;
    }

    public void processRetryableCommands(Instant cutoffTime) {
        log.info("Processing retryable commands");

        List<DeviceCommand> retryableCommands = commandRepository.findPendingAndRetryableCommands(cutoffTime);

        for (DeviceCommand command : retryableCommands) {
            if (command.canRetry()) {
                command.incrementRetryCount();
                command.setStatus(CommandStatus.PENDING);
                log.debug("Command {} marked for retry", command.getCommandId());
            }
        }

        commandRepository.saveAll(retryableCommands);
        log.info("Processed {} retryable commands", retryableCommands.size());
    }
}