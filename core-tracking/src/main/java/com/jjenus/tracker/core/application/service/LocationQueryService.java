package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.LocationResponse;
import com.jjenus.tracker.core.api.dto.LocationSearchRequest;
import com.jjenus.tracker.core.api.dto.PagedResponse;
import com.jjenus.tracker.core.domain.entity.TrackerLocation;
import com.jjenus.tracker.core.infrastructure.repository.TrackerLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LocationQueryService {

    private final TrackerLocationRepository locationRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "locations", key = "#locationId", unless = "#result == null")
    public LocationResponse getLocation(Long locationId) {
        log.debug("Cache miss - Fetching location: {}", locationId);
        TrackerLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + locationId));
        return toResponse(location);
    }

    @Cacheable(value = "locations", key = "'tracker_' + #trackerId")
    public List<LocationResponse> getTrackerLocations(String trackerId) {
        log.debug("Cache miss - Fetching locations for tracker: {}", trackerId);
        return locationRepository.findByTrackerTrackerIdOrderByRecordedAtDesc(trackerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "locationsPaged", key = "'search_' + #request.hashCode()")
    public PagedResponse<LocationResponse> searchLocations(LocationSearchRequest request) {
        log.debug("Cache miss - Searching locations: {}", request);

        Specification<TrackerLocation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getTrackerId())) {
                predicates.add(cb.equal(root.get("tracker").get("trackerId"), request.getTrackerId()));
            }

            if (request.getFromTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recordedAt"), request.getFromTime()));
            }

            if (request.getToTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recordedAt"), request.getToTime()));
            }

            if (request.getAccStatus() != null) {
                predicates.add(cb.equal(root.get("accStatus"), request.getAccStatus()));
            }

            if (request.getMinSpeed() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("speedKmh"), request.getMinSpeed()));
            }

            if (request.getMaxSpeed() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("speedKmh"), request.getMaxSpeed()));
            }

            if (Boolean.TRUE.equals(request.getValidOnly())) {
                predicates.add(cb.isNotNull(root.get("latitude")));
                predicates.add(cb.isNotNull(root.get("longitude")));
                predicates.add(cb.equal(root.get("validity"), "A"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(request.getSortDirection(), request.getSortBy())
        );

        Page<TrackerLocation> page = locationRepository.findAll(spec, pageable);
        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "locations", key = "'latestDevice_' + #deviceId", unless = "#result == null")
    public LocationResponse getLatestLocationByDeviceId(String deviceId) {
        log.debug("Cache miss - Fetching latest location for device: {}", deviceId);
        TrackerLocation location = locationRepository.findLatestByDeviceId(deviceId);
        return location != null ? toResponse(location) : null;
    }

    @Cacheable(value = "locationStats", key = "'trackerCount_' + #trackerId")
    public Long getLocationCountByTracker(String trackerId) {
        log.debug("Cache miss - Fetching location count for tracker: {}", trackerId);
        return locationRepository.countByTrackerId(trackerId);
    }

    @Cacheable(value = "locations", key = "'accOffEvents_' + #startTime")
    public List<LocationResponse> getAccOffEvents(Instant startTime) {
        log.debug("Cache miss - Fetching ACC off events from: {}", startTime);
        return locationRepository.findAccOffEvents(startTime).stream()
                .map(this::toResponse)
                .toList();
    }

    private LocationResponse toResponse(TrackerLocation location) {
        return modelMapper.map(location, LocationResponse.class);
    }
}