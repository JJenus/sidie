package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.infrastructure.repository.TrackerRepository;
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
public class TrackerQueryService {

    private final TrackerRepository trackerRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "trackers", key = "#id", unless = "#result == null")
    public TrackerResponse getTracker(String id) {
        log.debug("Cache miss - Fetching tracker: {}", id);
        Tracker tracker = trackerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + id));
        return toResponse(tracker);
    }

    @Cacheable(value = "trackers", key = "'byTracker_' + #trackerId", unless = "#result == null")
    public TrackerResponse getTrackerByTrackerId(String tracker_id) {
        log.debug("Cache miss - Fetching tracker by trackerId: {}", tracker_id);
        Tracker tracker = trackerRepository.findByTrackerId(tracker_id)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found for tracker id: " + tracker_id));
        return toResponse(tracker);
    }

    @Cacheable(value = "trackers", key = "'online'")
    public List<TrackerResponse> getOnlineTrackers() {
        log.debug("Cache miss - Fetching online trackers");
        return trackerRepository.findByIsOnline(true).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "trackers", key = "'staleConnections_' + #cutoffTime")
    public List<TrackerResponse> getStaleConnections(Instant cutoffTime) {
        log.debug("Cache miss - Fetching stale connections");
        return trackerRepository.findStaleConnections(cutoffTime).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "trackers", key = "'lowBattery_' + #threshold")
    public List<TrackerResponse> getTrackersWithLowBattery(float threshold) {
        log.debug("Cache miss - Fetching trackers with low battery");
        return trackerRepository.findTrackersWithLowBattery(threshold).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "trackers", key = "'exists_' + #trackerId")
    public boolean exists(String trackerId) {
        return trackerRepository.existsById(trackerId);
    }

    private TrackerResponse toResponse(Tracker tracker) {
        TrackerResponse response = modelMapper.map(tracker, TrackerResponse.class);

        // Map vehicle ID if present
        if (tracker.getVehicle() != null) {
            response.setVehicleId(tracker.getVehicle().getVehicleId());
        }

        return response;
    }

    @Cacheable(value = "trackers",
            key = "'list_page_' + #page + '_size_' + #size + '_sort_' + #sortBy + '_' + #sortDirection",
            unless = "#result == null")
    public PagedResponse<TrackerResponse> getTrackersList(
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        log.debug("Cache miss - Fetching trackers list: page={}, size={}, sort={} {}",
                page, size, sortDirection, sortBy);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortDirection), sortBy)
        );

        Page<Tracker> pageResult = trackerRepository.findAll(pageable);
        Page<TrackerResponse> mappedPage = pageResult.map(this::toResponse);

        return new PagedResponse<>(mappedPage);
    }
}