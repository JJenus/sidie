package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.TrackerRequest;
import com.jjenus.tracker.core.api.dto.TrackerResponse;
import com.jjenus.tracker.core.api.dto.TrackerStatusRequest;
import com.jjenus.tracker.core.api.dto.TrackerUpdateRequest;
import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.infrastructure.repository.TrackerRepository;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TrackerCommandService {

    private final TrackerRepository trackerRepository;
    private final VehicleRepository vehicleRepository;
    private final TrackerQueryService trackerQueryService;
    private final ModelMapper modelMapper;

    @Caching(evict = {
            @CacheEvict(value = "trackers", key = "#request.trackerId"),
            @CacheEvict(value = "trackers", key = "'byDevice_' + #request.deviceId"),
            @CacheEvict(value = "trackers", key = "'online'"),
            @CacheEvict(value = "vehicles", condition = "#request.vehicleId != null",
                    key = "'byDevice_' + #request.deviceId")
    })
    public TrackerResponse createTracker(TrackerRequest request) {
        log.info("Creating tracker: {}", request.getTrackerId());

        validateTrackerCreation(request);

        Tracker tracker = modelMapper.map(request, Tracker.class);

        if (request.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + request.getVehicleId()));
            tracker.setVehicle(vehicle);
        }

        Tracker saved = trackerRepository.save(tracker);

        log.info("Tracker created: {}", saved.getTrackerId());
        return trackerQueryService.getTracker(saved.getTrackerId());
    }

    @Caching(evict = {
            @CacheEvict(value = "trackers", key = "#trackerId"),
            @CacheEvict(value = "trackers", key = "'byDevice_*'"),
            @CacheEvict(value = "trackers", key = "'online'"),
            @CacheEvict(value = "vehicles", condition = "#request.vehicleId != null",
                    key = "'byDevice_*'")
    })
    public TrackerResponse updateTracker(String trackerId, TrackerUpdateRequest request) {
        log.info("Updating tracker: {}", trackerId);

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        if (request.getModel() != null) {
            tracker.setModel(request.getModel());
        }

        if (request.getProtocol() != null) {
            tracker.setProtocol(request.getProtocol());
        }

        if (request.getFirmwareVersion() != null) {
            tracker.setFirmwareVersion(request.getFirmwareVersion());
        }

        if (request.getSimNumber() != null) {
            tracker.setSimNumber(request.getSimNumber());
        }

        if (request.getBatteryLevel() != null) {
            tracker.setBatteryLevel(request.getBatteryLevel());
        }

        if (request.getSignalStrength() != null) {
            tracker.setSignalStrength(request.getSignalStrength());
        }

        if (request.getIsOnline() != null) {
            tracker.setIsOnline(request.getIsOnline());
            if (request.getIsOnline()) {
                tracker.setLastSeen(Instant.now());
            }
        }

        if (request.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + request.getVehicleId()));
            tracker.setVehicle(vehicle);
        }

        Tracker saved = trackerRepository.save(tracker);

        log.info("Tracker updated: {}", saved.getTrackerId());
        return trackerQueryService.getTracker(saved.getTrackerId());
    }

    @Caching(evict = {
            @CacheEvict(value = "trackers", key = "#trackerId"),
            @CacheEvict(value = "trackers", key = "'byDevice_*'"),
            @CacheEvict(value = "trackers", key = "'online'")
    })
    public void deleteTracker(String trackerId) {
        log.info("Deleting tracker: {}", trackerId);

        if (!trackerRepository.existsById(trackerId)) {
            throw new IllegalArgumentException("Tracker not found: " + trackerId);
        }

        trackerRepository.deleteById(trackerId);
        log.info("Tracker deleted: {}", trackerId);
    }

    @Caching(evict = {
            @CacheEvict(value = "trackers", key = "#trackerId"),
            @CacheEvict(value = "trackers", key = "'byDevice_*'"),
            @CacheEvict(value = "trackers", key = "'online'"),
            @CacheEvict(value = "trackers", key = "'lowBattery_*'")
    })
    public TrackerResponse updateTrackerStatus(String trackerId, TrackerStatusRequest request) {
        log.info("Updating tracker {} status", trackerId);

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        if (request.getBatteryLevel() != null) {
            tracker.setBatteryLevel(request.getBatteryLevel());
        }

        if (request.getSignalStrength() != null) {
            tracker.setSignalStrength(request.getSignalStrength());
        }

        if (request.getIsOnline() != null) {
            tracker.setIsOnline(request.getIsOnline());
            if (request.getIsOnline()) {
                tracker.updateLastSeen();
            }
        }

        Tracker saved = trackerRepository.save(tracker);

        log.info("Tracker status updated: {}", saved.getTrackerId());
        return trackerQueryService.getTracker(saved.getTrackerId());
    }

    @Caching(evict = {
            @CacheEvict(value = "trackers", key = "'staleConnections_*'"),
            @CacheEvict(value = "trackers", key = "'online'")
    })
    public void markStaleTrackersOffline(Instant cutoffTime) {
        log.info("Marking stale trackers offline before {}", cutoffTime);

        var staleTrackers = trackerRepository.findStaleConnections(cutoffTime);
        for (Tracker tracker : staleTrackers) {
            tracker.setIsOnline(false);
        }

        trackerRepository.saveAll(staleTrackers);
        log.info("Marked {} trackers offline", staleTrackers.size());
    }

    private void validateTrackerCreation(TrackerRequest request) {
        if (trackerRepository.existsById(request.getTrackerId())) {
            throw new IllegalArgumentException("Tracker with ID " + request.getTrackerId() + " already exists");
        }

        if (trackerRepository.existsBySimNumber(request.getSimNumber())) {
            throw new IllegalArgumentException("Tracker with sim number " + request.getSimNumber() + " already exists");
        }
    }
}