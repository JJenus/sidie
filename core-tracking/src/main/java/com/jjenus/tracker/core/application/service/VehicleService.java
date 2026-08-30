package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.domain.entity.*;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
import com.jjenus.tracker.core.domain.enums.TripStartReason;
import com.jjenus.tracker.core.infrastructure.repository.*;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class VehicleService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepository;
    private final TrackerRepository trackerRepository;
    private final TripRepository tripRepository;
    private final TrackerLocationRepository locationRepository;

    public VehicleService(VehicleRepository vehicleRepository,
                          TrackerRepository trackerRepository,
                          TripRepository tripRepository,
                          TrackerLocationRepository locationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.trackerRepository = trackerRepository;
        this.tripRepository = tripRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public Vehicle saveVehicle(Vehicle vehicle) {
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public void updateVehicleAccStatus(String vehicleId, boolean accStatus, Instant timestamp) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));

        vehicle.updateAccStatus(accStatus, timestamp);
        vehicleRepository.save(vehicle);
    }

    @Transactional
    public void updateVehicleLocation(String vehicleId, LocationPoint locationPoint) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));

        TrackerLocation location = new TrackerLocation(
                locationPoint.latitude(),
                locationPoint.longitude(),
                locationPoint.speedKmh(),
                locationPoint.timestamp()
        );

        if (vehicle.getDeviceId() != null) {
            trackerRepository.findByDeviceId(vehicle.getDeviceId())
                .ifPresent(tracker -> {
                    location.setTracker(tracker);
                    tracker.updateLastSeen();
                    trackerRepository.save(tracker);
                });
        }

        TrackerLocation saved = locationRepository.save(location);

        vehicle.updateLocation(saved);
        vehicleRepository.save(vehicle);

        logger.debug("Saved location for vehicle {}: lat={}, lon={}, speed={} km/h",
                vehicleId, locationPoint.latitude(), locationPoint.longitude(), locationPoint.speedKmh());
    }
    
    @Transactional
    public Trip startTrip(String vehicleId, TrackerLocation startLocation, TripStartReason reason) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        
        Trip trip = new Trip();
        trip.setTripId("TRIP_" + vehicleId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        trip.setVehicle(vehicle);
        trip.setStartLocation(startLocation);
        trip.setStartTime(startLocation.getRecordedAt());
        trip.setStartReason(reason);
        trip.setIsActive(true);
        
        // Save start location
        locationRepository.save(startLocation);
        
        vehicle.addTrip(trip);
        vehicleRepository.save(vehicle);
        
        return tripRepository.save(trip);
    }
    
    @Transactional
    public void endTrip(String tripId, TrackerLocation endLocation, TripEndReason reason) {
        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        
        if (endLocation != null) {
            locationRepository.save(endLocation);
        }
        
        trip.endTrip(reason, endLocation);
        tripRepository.save(trip);
    }
    
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicle(String vehicleId) {
        return vehicleRepository.findById(vehicleId);
    }
    
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicleByDeviceId(String deviceId) {
        return vehicleRepository.findByDeviceId(deviceId);
    }
    
    @Transactional(readOnly = true)
    public Optional<Trip> getActiveTrip(String vehicleId) {
        return tripRepository.findByVehicleVehicleIdAndIsActive(vehicleId, true);
    }

    public String findVehicleIdForDevice(String deviceId) {
        Tracker tracker = trackerRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + deviceId));

        Vehicle vehicle = tracker.getVehicle();
        if (vehicle == null) {
            throw new IllegalStateException(
                    String.format("Tracker %s is not assigned to a vehicle", deviceId));
        }
        return vehicle.getVehicleId();
    }
}