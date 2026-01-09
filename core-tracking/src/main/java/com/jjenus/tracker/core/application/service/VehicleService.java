package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.domain.entity.*;
import com.jjenus.tracker.core.infrastructure.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleService {
    
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
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        
        vehicle.updateAccStatus(accStatus, timestamp);
        vehicleRepository.save(vehicle);
    }
    
    @Transactional
    public void updateVehicleLocation(String vehicleId, TrackerLocation location) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        
        // Update tracker last seen
        if (vehicle.getDeviceId() != null) {
            trackerRepository.findByDeviceId(vehicle.getDeviceId())
                .ifPresent(tracker -> {
                    tracker.updateLastSeen();
                    trackerRepository.save(tracker);
                });
        }
        
        // Save location
        locationRepository.save(location);
        
        // Update vehicle
        vehicle.updateLocation(location);
        vehicleRepository.save(vehicle);
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
}