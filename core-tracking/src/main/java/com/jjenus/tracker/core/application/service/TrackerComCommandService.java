package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.TrackerCommandRequest;
import com.jjenus.tracker.core.api.dto.TrackerCommandResponse;
import com.jjenus.tracker.core.domain.entity.TrackerCommand;
import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.infrastructure.repository.TrackerCommandRepository;
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
public class TrackerComCommandService {

    private final TrackerCommandRepository commandRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerComQueryService commandQueryService;
    private final ModelMapper modelMapper;

    @Caching(evict = {
            @CacheEvict(value = "commands", key = "'tracker_' + #request.trackerId + '_*'"),
            @CacheEvict(value = "commands", key = "'status_PENDING'"),
            @CacheEvict(value = "commands", key = "'trackerStatus_' + #request.trackerId + '_PENDING'"),
            @CacheEvict(value = "commandStats", key = "'pendingCount_' + #request.trackerId")
    })
    public TrackerCommandResponse createCommand(TrackerCommandRequest request) {
        log.info("Creating command for tracker: {}", request.getTrackerId());

        Tracker tracker = trackerRepository.findById(request.getTrackerId())
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + request.getTrackerId()));

        TrackerCommand command = modelMapper.map(request, TrackerCommand.class);
        command.setTracker(tracker);
        command.setStatus(CommandStatus.PENDING);

        TrackerCommand saved = commandRepository.save(command);

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
    public TrackerCommandResponse markAsSent(Long commandId) {
        log.info("Marking command as sent: {}", commandId);

        TrackerCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsSent();
        TrackerCommand saved = commandRepository.save(command);

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
    public TrackerCommandResponse markAsDelivered(Long commandId, String response) {
        log.info("Marking command as delivered: {}", commandId);

        TrackerCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsDelivered(response);
        TrackerCommand saved = commandRepository.save(command);

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
    public TrackerCommandResponse markAsFailed(Long commandId, String error) {
        log.info("Marking command as failed: {}", commandId);

        TrackerCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.markAsFailed(error);
        TrackerCommand saved = commandRepository.save(command);

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
    public TrackerCommandResponse retryCommand(Long commandId) {
        log.info("Retrying command: {}", commandId);

        TrackerCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        if (!command.canRetry()) {
            throw new IllegalStateException("Command cannot be retried");
        }

        command.incrementRetryCount();
        command.setStatus(CommandStatus.PENDING);
        TrackerCommand saved = commandRepository.save(command);

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

        List<TrackerCommand> retryableCommands = commandRepository.findPendingAndRetryableCommands(cutoffTime);

        for (TrackerCommand command : retryableCommands) {
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