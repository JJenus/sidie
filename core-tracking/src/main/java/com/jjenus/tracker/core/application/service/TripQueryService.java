package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.domain.entity.Trip;
import com.jjenus.tracker.core.exception.TripException;
import com.jjenus.tracker.core.infrastructure.repository.TripRepository;
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
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TripQueryService {

    private final TripRepository tripRepository;
    private final ModelMapper modelMapper;
    private final LocationQueryService locationQueryService;

    @Cacheable(value = "trips", key = "#tripId", unless = "#result == null")
    public TripResponse getTrip(String tripId) {
        log.debug("Cache miss - Fetching trip: {}", tripId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripException.notFound(tripId));
        return toResponse(trip);
    }

    @Cacheable(value = "trips", key = "'active_' + #vehicleId", unless = "#result == null")
    public TripResponse getActiveTrip(String vehicleId) {
        log.debug("Cache miss - Fetching active trip for vehicle: {}", vehicleId);
        Trip trip = tripRepository.findByVehicleVehicleIdAndIsActive(vehicleId, true)
                .orElseThrow(() -> TripException.notActive(vehicleId));
        return toResponse(trip);
    }

    @Cacheable(value = "trips", key = "'vehicle_' + #vehicleId")
    public List<TripResponse> getVehicleTrips(String vehicleId) {
        log.debug("Cache miss - Fetching trips for vehicle: {}", vehicleId);
        return tripRepository.findByVehicleVehicleId(vehicleId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "tripsPaged", key = "'search_' + #request.hashCode()")
    public PagedResponse<TripResponse> searchTrips(TripSearchRequest request) {
        log.debug("Cache miss - Searching trips: {}", request);

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(request.getSortDirection(), request.getSortBy())
        );

        Page<Trip> page;

        if (StringUtils.hasText(request.getVehicleId())) {
            if (request.getStartTimeFrom() != null && request.getStartTimeTo() != null) {
                page = tripRepository.findByVehicleVehicleIdAndStartTimeBetween(
                        request.getVehicleId(),
                        request.getStartTimeFrom(),
                        request.getStartTimeTo(),
                        pageable
                );
            } else {
                page = tripRepository.findByVehicleVehicleId(request.getVehicleId(), pageable);
            }
        } else {
            page = tripRepository.findAll(pageable);
        }

        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "trips", key = "'activeTrips'")
    public List<TripResponse> getActiveTrips() {
        log.debug("Cache miss - Fetching all active trips");
        return tripRepository.findByIsActive(true).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "tripStats", key = "'vehicleStats_' + #vehicleId + '_' + #startTime + '_' + #endTime")
    public TripStatisticsResponse getTripStatistics(String vehicleId, Instant startTime, Instant endTime) {
        log.debug("Cache miss - Fetching trip statistics for vehicle: {}", vehicleId);

        List<Trip> trips = tripRepository.findByVehicleVehicleIdAndStartTimeBetween(
                vehicleId, startTime, endTime);

        TripStatisticsResponse stats = new TripStatisticsResponse();
        stats.setVehicleId(vehicleId);
        stats.setPeriodStart(startTime);
        stats.setPeriodEnd(endTime);
        stats.setTotalTrips(trips.size());

        float totalDistance = 0;
        float totalFuel = 0;
        int totalIdleTime = 0;
        Duration totalDuration = Duration.ZERO;

        for (Trip trip : trips) {
            if (trip.getTotalDistanceKm() != null) {
                totalDistance += trip.getTotalDistanceKm();
            }
            if (trip.getFuelConsumedLiters() != null) {
                totalFuel += trip.getFuelConsumedLiters();
            }
            if (trip.getIdleTimeMinutes() != null) {
                totalIdleTime += trip.getIdleTimeMinutes();
            }
            if (trip.getStartTime() != null && trip.getEndTime() != null) {
                totalDuration = totalDuration.plus(Duration.between(trip.getStartTime(), trip.getEndTime()));
            }
        }

        stats.setTotalDistanceKm(totalDistance);
        stats.setTotalFuelConsumedLiters(totalFuel);
        stats.setTotalIdleTimeMinutes(totalIdleTime);
        stats.setTotalDuration(totalDuration);

        if (trips.size() > 0) {
            stats.setAverageDistanceKm(totalDistance / trips.size());
            stats.setAverageFuelConsumption(totalFuel / trips.size());

            if (totalDuration.toHours() > 0) {
                stats.setAverageSpeedKmh(totalDistance / totalDuration.toHours());
            }
        }

        return stats;
    }

    @Cacheable(value = "tripStats", key = "'distance_' + #vehicleId + '_' + #startTime + '_' + #endTime")
    public Float getTotalDistanceForPeriod(String vehicleId, Instant startTime, Instant endTime) {
        log.debug("Cache miss - Fetching total distance for vehicle: {}", vehicleId);
        Float distance = tripRepository.getTotalDistanceForPeriod(vehicleId, startTime, endTime);
        return distance != null ? distance : 0.0f;
    }

    private TripResponse toResponse(Trip trip) {
        TripResponse response = modelMapper.map(trip, TripResponse.class);
        response.setVehicleId(trip.getVehicle().getVehicleId());

        // Map locations if present
        if (trip.getStartLocation() != null) {
            response.setStartLocation(modelMapper.map(trip.getStartLocation(), LocationResponse.class));
        }
        if (trip.getEndLocation() != null) {
            response.setEndLocation(modelMapper.map(trip.getEndLocation(), LocationResponse.class));
        }

        // Calculate duration
        if (trip.getStartTime() != null) {
            if (trip.getEndTime() != null) {
                response.setDuration(Duration.between(trip.getStartTime(), trip.getEndTime()));
            } else {
                response.setDuration(Duration.between(trip.getStartTime(), Instant.now()));
            }
        }

        return response;
    }
}