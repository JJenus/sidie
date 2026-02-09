package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.DeviceCommandResponse;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.domain.entity.DeviceCommand;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.infrastructure.repository.DeviceCommandRepository;
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
public class DeviceCommandQueryService {

    private final DeviceCommandRepository commandRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "commands", key = "#commandId", unless = "#result == null")
    public DeviceCommandResponse getCommand(Long commandId) {
        log.debug("Cache miss - Fetching command: {}", commandId);
        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));
        return toResponse(command);
    }

    @Cacheable(value = "commands", key = "'tracker_' + #trackerId + '_' + #page + '_' + #size")
    public PagedResponse<DeviceCommandResponse> getCommandsByTracker(String trackerId, int page, int size) {
        log.debug("Cache miss - Fetching commands for tracker: {}", trackerId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DeviceCommand> commands = commandRepository.findByTrackerTrackerId(trackerId, pageable);

        return new PagedResponse<>(commands.map(this::toResponse));
    }

    @Cacheable(value = "commands", key = "'status_' + #status")
    public List<DeviceCommandResponse> getCommandsByStatus(CommandStatus status) {
        log.debug("Cache miss - Fetching commands by status: {}", status);
        return commandRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'trackerStatus_' + #trackerId + '_' + #status")
    public List<DeviceCommandResponse> getCommandsByTrackerAndStatus(String trackerId, CommandStatus status) {
        log.debug("Cache miss - Fetching commands for tracker {} with status {}", trackerId, status);
        return commandRepository.findByTrackerTrackerIdAndStatus(trackerId, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'pendingRetryable_' + #cutoffTime")
    public List<DeviceCommandResponse> getPendingAndRetryableCommands(Instant cutoffTime) {
        log.debug("Cache miss - Fetching pending and retryable commands");
        return commandRepository.findPendingAndRetryableCommands(cutoffTime).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commands", key = "'recentDevice_' + #deviceId")
    public List<DeviceCommandResponse> getRecentCommandsByDeviceId(String deviceId) {
        log.debug("Cache miss - Fetching recent commands for device: {}", deviceId);
        return commandRepository.findRecentCommandsByDeviceId(deviceId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "commandStats", key = "'pendingCount_' + #trackerId")
    public Long getPendingCommandCount(String trackerId) {
        log.debug("Cache miss - Fetching pending command count for tracker: {}", trackerId);
        return commandRepository.countPendingCommands(trackerId);
    }

    private DeviceCommandResponse toResponse(DeviceCommand command) {
        DeviceCommandResponse response = modelMapper.map(command, DeviceCommandResponse.class);
        response.setTrackerId(command.getTracker().getTrackerId());
        return response;
    }
}