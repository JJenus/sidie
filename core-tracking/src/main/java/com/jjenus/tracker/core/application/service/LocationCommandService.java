package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.LocationResponse;
import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.entity.TrackerLocation;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.infrastructure.repository.TrackerLocationRepository;
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
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LocationCommandService {

    private final TrackerLocationRepository locationRepository;
    private final TrackerRepository trackerRepository;
    private final VehicleRepository vehicleRepository;
    private final LocationQueryService locationQueryService;
    private final ModelMapper modelMapper;

//    Find Active trip and add location
    @Caching(evict = {
            @CacheEvict(value = "locations", key = "'tracker_' + #trackerId"),
            @CacheEvict(value = "locations", key = "'latestDevice_' + #deviceId"),
            @CacheEvict(value = "locationsPaged", key = "'search_*'"),
            @CacheEvict(value = "locations", key = "'accOffEvents_*'"),
            @CacheEvict(value = "vehicles", key = "#vehicleId", condition = "#vehicleId != null"),
            @CacheEvict(value = "trips", key = "'active_' + #vehicleId", condition = "#vehicleId != null")
    })
    public LocationResponse recordLocation(String trackerId, Double latitude,
                                           Double longitude, Float speedKmh, Instant recordedAt, Map<String, Object> meta) {
        log.debug("Recording location for tracker: {} at [{}, {}]", trackerId, latitude, longitude);

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        TrackerLocation location = new TrackerLocation(latitude, longitude, speedKmh, recordedAt);
        location.setTracker(tracker);
        location.setValidity(location.isValid() ? "A" : "V");

        /*  todo:
         * use meta to set the rest fields. Make a constants, use that as keys
         * to populate meta in device comm and extract data here.
         */

        // Update tracker last seen
        tracker.updateLastSeen();
        trackerRepository.save(tracker);

        // Update vehicle location if assigned
        if (tracker.getVehicle() != null) {
            Vehicle vehicle = tracker.getVehicle();
            vehicle.updateLocation(location);
            vehicleRepository.save(vehicle);
        }

        TrackerLocation saved = locationRepository.save(location);

        log.info("Location recorded: {} for tracker: {}", saved.getLocationId(), trackerId);
        return locationQueryService.getLocation(saved.getLocationId());
    }

    @Caching(evict = {
            @CacheEvict(value = "locations", key = "#locationId"),
            @CacheEvict(value = "locations", key = "'tracker_*'"),
            @CacheEvict(value = "locationsPaged", key = "'search_*'"),
            @CacheEvict(value = "locationStats", key = "'trackerCount_*'")
    })
    public void deleteLocation(Long locationId) {
        log.info("Deleting location: {}", locationId);

        if (!locationRepository.existsById(locationId)) {
            throw new IllegalArgumentException("Location not found: " + locationId);
        }

        locationRepository.deleteById(locationId);
        log.info("Location deleted: {}", locationId);
    }

    @Caching(evict = {
            @CacheEvict(value = "locations", allEntries = true),
            @CacheEvict(value = "locationsPaged", allEntries = true),
            @CacheEvict(value = "locationStats", allEntries = true)
    })
    public int cleanupOldLocations(Instant cutoffTime) {
        log.info("Cleaning up old locations before: {}", cutoffTime);

        // In production, use batch delete or archive instead
        int deleted = 0;
        // This should be implemented with batch processing or scheduled job
        // locationRepository.deleteByRecordedAtBefore(cutoffTime);
        log.info("Cleaned up {} old locations", deleted);
        return deleted;
    }

    @Caching(evict = {
            @CacheEvict(value = "locations", key = "#locationId"),
            @CacheEvict(value = "locations", key = "'tracker_*'"),
            @CacheEvict(value = "locationsPaged", key = "'search_*'")
    })
    public LocationResponse updateLocationData(Long locationId, String field, String value) {
        log.info("Updating location {} field {} to {}", locationId, field, value);

        TrackerLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + locationId));

        switch (field.toLowerCase()) {
            case "validity":
                location.setValidity(value);
                break;
            case "engine_status":
                location.setEngineStatus(value);
                break;
            case "device_status":
                location.setDeviceStatus(value);
                break;
            default:
                throw new IllegalArgumentException("Invalid field to update: " + field);
        }

        TrackerLocation saved = locationRepository.save(location);
        log.info("Location updated: {}", locationId);
        return locationQueryService.getLocation(saved.getLocationId());
    }
}