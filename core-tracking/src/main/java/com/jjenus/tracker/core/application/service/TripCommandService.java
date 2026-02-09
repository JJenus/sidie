package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.TripResponse;
import com.jjenus.tracker.core.domain.entity.TrackerLocation;
import com.jjenus.tracker.core.domain.entity.Trip;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
import com.jjenus.tracker.core.domain.enums.TripStartReason;
import com.jjenus.tracker.core.exception.TripException;
import com.jjenus.tracker.core.infrastructure.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TripCommandService {

    private final TripRepository tripRepository;
    private final TripQueryService tripQueryService;

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "'active_' + #vehicleId"),
            @CacheEvict(value = "trips", key = "'activeTrips'"),
            @CacheEvict(value = "trips", key = "'search_*'"),
            @CacheEvict(value = "tripStats", allEntries = true),
            @CacheEvict(value = "vehicleStats", key = "'activeTrips'")
    })
    public TripResponse startTrip(Vehicle vehicle, TripStartReason reason, Instant startTime) {
        log.info("Starting trip for vehicle: {}", vehicle.getVehicleId());

        if (tripRepository.findByVehicleVehicleIdAndIsActive(vehicle.getVehicleId(), true).isPresent()) {
            throw TripException.alreadyActive(vehicle.getVehicleId());
        }

        Trip trip = new Trip();
        trip.setTripId(generateTripId(vehicle.getVehicleId()));
        trip.setVehicle(vehicle);
        trip.setStartTime(startTime);
        trip.setStartReason(reason);
        trip.setIsActive(true);

        if (vehicle.getCurrentLocation() != null) {
            trip.setStartLocation(vehicle.getCurrentLocation());
        }

        Trip saved = tripRepository.save(trip);

        log.info("Trip started: {}", saved.getTripId());
        return tripQueryService.getTrip(saved.getTripId());
    }

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "#tripId"),
            @CacheEvict(value = "trips", key = "'active_' + #vehicleId"),
            @CacheEvict(value = "trips", key = "'activeTrips'"),
            @CacheEvict(value = "trips", key = "'search_*'"),
            @CacheEvict(value = "tripStats", allEntries = true),
            @CacheEvict(value = "vehicleStats", key = "'activeTrips'")
    })
    public TripResponse endTrip(String tripId, TripEndReason reason) {
        log.info("Ending trip: {}", tripId);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripException.notFound(tripId));

        if (!trip.getIsActive()) {
            throw new IllegalStateException("Trip " + tripId + " is not active");
        }

        trip.setEndTime(Instant.now());
        trip.setEndReason(reason);
        trip.setIsActive(false);

        Trip saved = tripRepository.save(trip);

        log.info("Trip ended: {}", saved.getTripId());
        return tripQueryService.getTrip(saved.getTripId());
    }

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "'active_' + #vehicleId"),
            @CacheEvict(value = "trips", key = "'activeTrips'"),
            @CacheEvict(value = "trips", key = "'search_*'"),
            @CacheEvict(value = "tripStats", allEntries = true),
            @CacheEvict(value = "vehicleStats", key = "'activeTrips'")
    })
    public void endActiveTripForVehicle(String vehicleId, TripEndReason reason) {
        log.info("Ending active trip for vehicle: {}", vehicleId);

        Trip activeTrip = tripRepository.findByVehicleVehicleIdAndIsActive(vehicleId, true)
                .orElseThrow(() -> TripException.notActive(vehicleId));

        activeTrip.setEndTime(Instant.now());
        activeTrip.setEndReason(reason);
        activeTrip.setIsActive(false);

        tripRepository.save(activeTrip);
        log.info("Active trip ended for vehicle: {}", vehicleId);
    }

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "#tripId"),
            @CacheEvict(value = "trips", key = "'search_*'"),
            @CacheEvict(value = "tripStats", allEntries = true)
    })
    public void updateTripDistance(String tripId, float distanceKm) {
        log.info("Updating distance for trip: {} to {} km", tripId, distanceKm);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripException.notFound(tripId));

        trip.setTotalDistanceKm(distanceKm);
        tripRepository.save(trip);

        log.info("Trip distance updated: {}", tripId);
    }

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "#tripId"),
            @CacheEvict(value = "trips", key = "'search_*'"),
            @CacheEvict(value = "tripStats", allEntries = true)
    })
    public void updateTripFuelConsumption(String tripId, float fuelLiters) {
        log.info("Updating fuel consumption for trip: {} to {} liters", tripId, fuelLiters);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripException.notFound(tripId));

        trip.setFuelConsumedLiters(fuelLiters);
        tripRepository.save(trip);

        log.info("Trip fuel consumption updated: {}", tripId);
    }

    @Caching(evict = {
            @CacheEvict(value = "trips", key = "'active_' + #vehicleId"),
            @CacheEvict(value = "trips", key = "'activeTrips'"),
            @CacheEvict(value = "tripStats", allEntries = true)
    })
    public void addTripPoint(String vehicleId, TrackerLocation location) {

        Trip trip = tripRepository
                .findByVehicleVehicleIdAndIsActive(vehicleId, true)
                .orElse(null);

        if (trip == null) return;

        trip.addLocationPoint(location);

        tripRepository.save(trip);
    }

    private float tripDistance(TrackerLocation a, TrackerLocation b) {
        final double R = 6371.0;

        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double h =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(lat1) * Math.cos(lat2) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return (float) (R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)));
    }

    private String generateTripId(String vehicleId) {
        return "TRIP_" + vehicleId + "_" + Instant.now().toEpochMilli() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}