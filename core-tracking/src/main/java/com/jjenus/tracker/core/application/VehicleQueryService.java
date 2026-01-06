package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.core.infrastructure.IVehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleQueryService {
    private final IVehicleRepository vehicleRepository;
    
    public VehicleQueryService(IVehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }
    
    public Optional<Vehicle> getVehicleById(String vehicleId) {
        return vehicleRepository.findById(vehicleId);
    }
    
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }
    
    public Optional<LocationPoint> getCurrentLocation(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(Vehicle::getCurrentLocation);
    }
    
    public boolean isVehicleMoving(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(vehicle -> vehicle.getCurrentLocation().speedKmh() > 0)
            .orElse(false);
    }
    
    public Optional<Float> getVehicleSpeed(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(vehicle -> vehicle.getCurrentLocation().speedKmh());
    }
}
