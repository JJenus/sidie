package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.domain.entity.*;
import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.domain.enums.CommandType;
import com.jjenus.tracker.core.infrastructure.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceCommandService {
    @Autowired
    private DeviceCommandRepository commandRepository;
    @Autowired
    private TrackerRepository trackerRepository;

    @Transactional
    public DeviceCommand createCommand(String trackerId, CommandType commandType,
                                       String commandData, String initiatedBy) {

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        DeviceCommand command = new DeviceCommand();
        command.setTracker(tracker);
        command.setCommandType(commandType);
        command.setCommandData(commandData);
        command.setStatus(CommandStatus.PENDING);
        command.setInitiatedBy(initiatedBy);
        command.setCreatedAt(Instant.now());

        return commandRepository.save(command);
    }

    @Transactional
    public void updateCommandStatus(Long commandId, CommandStatus status,
                                    String responseData, String errorMessage) {

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.setStatus(status);
        command.setUpdatedAt(Instant.now());

        if (responseData != null) {
            command.setResponseData(responseData);
            command.setRespondedAt(Instant.now());
        }

        if (errorMessage != null) {
            command.setErrorMessage(errorMessage);
        }

        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsSent(Long commandId) {
        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsSent();
        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsDelivered(Long commandId, String response) {
        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsDelivered(response);
        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsFailed(Long commandId, String error) {
        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsFailed(error);
        commandRepository.save(command);
    }

    @Transactional(readOnly = true)
    public List<DeviceCommand> getPendingCommands() {
        return commandRepository.findByStatus(CommandStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<DeviceCommand> getCommandsByTracker(String trackerId) {
        return commandRepository.findByTrackerTrackerId(trackerId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public Optional<DeviceCommand> getCommand(Long commandId) {
        return commandRepository.findById(commandId);
    }

    @Transactional
    public void cleanupOldCommands(int daysToKeep) {
        Instant cutoffTime = Instant.now().minusSeconds(daysToKeep * 24 * 60 * 60);
        commandRepository.cleanupOldCommands(cutoffTime);
    }
}