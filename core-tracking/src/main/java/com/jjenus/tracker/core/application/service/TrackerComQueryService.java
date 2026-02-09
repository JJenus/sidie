package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.TrackerCommandResponse;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.domain.entity.TrackerCommand;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.infrastructure.repository.TrackerCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrackerComQueryService {

    private final TrackerCommandRepository commandRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "commands", key = "#commandId", unless = "#result == null")
    public TrackerCommandResponse getCommand(Long commandId) {
        log.debug("Cache miss - Fetching command: {}", commandId);
        TrackerCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));
        return toResponse(command);
    }

    @Cacheable(value = "commands", key = "'tracker_' + #trackerId + '_' + #page + '_' + #size")
    public PagedResponse<TrackerCommandResponse> getCommandsByTracker(String trackerId, int page, int size) {
        log.debug("Cache miss - Fetching commands for tracker: {}", trackerId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TrackerCommand> commands = commandRepository.findByTrackerTrackerId(trackerId, pageable);

        return new PagedResponse<>(commands.map(this::toResponse));
    }

    @Cacheable(value = "commands", key = "'status_' + #status")
    public List<TrackerCommandResponse> getCommandsByStatus(CommandStatus status) {
        log.debug("Cache miss - Fetching commands by status: {}", status);
        return commandRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'trackerStatus_' + #trackerId + '_' + #status")
    public List<TrackerCommandResponse> getCommandsByTrackerAndStatus(String trackerId, CommandStatus status) {
        log.debug("Cache miss - Fetching commands for tracker {} with status {}", trackerId, status);
        return commandRepository.findByTrackerTrackerIdAndStatus(trackerId, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'pendingRetryable_' + #cutoffTime")
    public List<TrackerCommandResponse> getPendingAndRetryableCommands(Instant cutoffTime) {
        log.debug("Cache miss - Fetching pending and retryable commands");
        return commandRepository.findPendingAndRetryableCommands(cutoffTime).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'recentTracker_' + #trackerId")
    public List<TrackerCommandResponse> getRecentCommandsByTrackerId(String trackerId) {
        log.debug("Cache miss - Fetching recent commands for tracker: {}", trackerId);
        return commandRepository.findRecentCommandsByTrackerId(trackerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commandStats", key = "'pendingCount_' + #trackerId")
    public Long getPendingCommandCount(String trackerId) {
        log.debug("Cache miss - Fetching pending command count for tracker: {}", trackerId);
        return commandRepository.countPendingCommands(trackerId);
    }

    private TrackerCommandResponse toResponse(TrackerCommand command) {
        TrackerCommandResponse response = modelMapper.map(command, TrackerCommandResponse.class);
        response.setTrackerId(command.getTracker().getTrackerId());
        return response;
    }
}